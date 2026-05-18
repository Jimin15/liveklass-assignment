# 동시성 처리 설계 — 비관적 락

## 문제 상황

정원이 1명인 강의에 여러 명이 동시에 신청할 때 Race Condition 발생:

```
정원 1명, reservedCount = 0

Thread A: reservedCount(0) 읽음 → 0 < 1 통과 → PENDING 저장
Thread B: reservedCount(0) 읽음 → 0 < 1 통과 → PENDING 저장  ← 정원 초과!
Thread C: reservedCount(0) 읽음 → 0 < 1 통과 → PENDING 저장  ← 정원 초과!
```

읽는 시점에 모두 0을 보므로 세 명 모두 통과해버림.

---

## 해결 방법: 비관적 락 (SELECT FOR UPDATE)

### DB 레벨 동작

```sql
SELECT * FROM klass WHERE id = 1 FOR UPDATE;
-- 이 행을 내가 잠금. 다른 트랜잭션은 같은 행에 FOR UPDATE 시 대기.
-- 내 트랜잭션 커밋/롤백 → 잠금 해제 → 대기 트랜잭션 진행
```

### JPA 구현

```java
// KlassRepository — 정원/대기열 변경이 필요한 모든 경로
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
@Query("SELECT k FROM Klass k WHERE k.id = :id")
Optional<Klass> findByIdWithLock(@Param("id") Long id);

// EnrollmentRepository — 결제 확정 시 enrollment 행 단독 락
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT e FROM Enrollment e WHERE e.registrationId = :registrationId")
Optional<Enrollment> findByRegistrationIdWithLock(@Param("registrationId") Long registrationId);

// WaitlistEntryRepository — 승격 대상 조회 시 방어적 락
// (klass 락으로 이미 직렬화되지만 상태 변경 대상이므로 명시적 락 추가)
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<WaitlistEntry> findFirstByKlassIdAndStatusOrderBySequenceAsc(
        Long klassId, WaitlistStatus status);
```

**@Transactional 없으면 락이 즉시 해제돼서 의미 없음**

---

## 동시 요청 시나리오

```
정원 1명, reservedCount = 0

T=0  Thread A: findByIdWithLock() → 락 획득
T=0  Thread B: findByIdWithLock() → 락 대기...
T=0  Thread C: findByIdWithLock() → 락 대기...

T=1  Thread A: reservedCount(0) < capacity(1) → enrollment 생성(PENDING), reservedCount=1, 커밋 → 락 해제
T=2  Thread B: 락 획득 → reservedCount(1) >= capacity(1) → waitlist_entry 생성(sequence=1), 커밋 → 락 해제
T=3  Thread C: 락 획득 → reservedCount(1) >= capacity(1) → waitlist_entry 생성(sequence=2), 커밋 → 락 해제

결과: 성공 1건, 대기 2건 → 정원 초과 없음
```

---

## 락 순서 원칙 (데드락 방지)

```
klass 상태/정원/대기열을 변경하는 작업
  → 항상 klass를 먼저 잠근다
  → 이후 registration / enrollment / waitlist_entry 조회 및 상태 변경

결제 확정처럼 klass 값을 변경하지 않는 작업
  → enrollment / registration 행만 잠근다
```

모든 경로에서 락 획득 순서를 일관되게 유지해 데드락을 방지한다.

---

## 수강 신청 플로우

```
① findByIdWithLock(klassId)          klass 락 획득
② klass.status != OPEN               → 400
③ 중복 신청 확인 (registration PENDING/CONFIRMED/WAITLISTED 존재)  → 409
④ reservedCount >= capacity
     → klass.waitlist_next_sequence 원자적 증가 → waitlist_entry 생성(WAITING)
       class_registration 생성(WAITLISTED)
   reservedCount < capacity
     → reservedCount++, enrollment 생성(PENDING)
       class_registration 생성(PENDING)
⑤ 커밋                              락 해제
```

**중복 신청 확인(③)을 반드시 락 안에서 수행해야 하는 이유:**
`class_registration(klass_id, user_id)` 인덱스는 유니크가 아님 (CANCELLED 후 재신청 허용 정책).
DB 레벨 보호가 없으므로 락 밖에서 체크하면 TOCTOU 발생:
```
Thread A: 중복 없음 확인 → Thread B: 중복 없음 확인 → 둘 다 저장 → 중복!
```

**중복 방지 한계 및 실서비스 보강 방안 (MySQL):**
과제에서는 애플리케이션 레벨 검사로 충분하나, 실서비스에서는 `active_key` 컬럼을 활용한
DB 레벨 안전장치를 추가할 수 있다:
```sql
-- PENDING/CONFIRMED/WAITLISTED → active_key = 1
-- CANCELLED → active_key = NULL (MySQL unique index는 NULL 중복 허용)
UNIQUE KEY uk_active_registration (klass_id, user_id, active_key)
```

---

## 결제 확정 플로우

결제 확정은 reservedCount를 변경하지 않으므로 **klass 락 불필요**.
같은 enrollment에 확정/취소가 동시에 들어올 수 있으므로 **enrollment 행 락** 필요:

```
동시 요청 위험 시나리오:
Thread A: 결제 확정 → PENDING 읽음 → CONFIRMED 저장
Thread B: 수강 취소 → PENDING 읽음 → CANCELLED 저장  → flush 순서에 따라 상태 꼬임
```

```
① findByRegistrationIdWithLock()    enrollment 행 락 획득
② registration.status == PENDING 검증  → 400
③ enrollment.status == PENDING 검증    → 400
④ enrollment → CONFIRMED, confirmed_at = now(), cancel_deadline_at = now() + 7일
   registration → CONFIRMED
⑤ 커밋                              락 해제
```

---

## 취소 플로우 (통합)

**모든 취소는 klass 락으로 직렬화** — WAITLISTED 취소도 포함.
(대기자 승격과 대기 취소가 동시에 발생하면 이미 취소된 대기자가 승격될 수 있음)

```
① findByIdWithLock(klassId)                   klass 락 획득
② registration / enrollment / waitlist_entry 현재 상태 재조회
③ 이미 CANCELLED이면 → 409 (중복 취소 방지)
④ [CONFIRMED 한정] cancel_deadline_at 재검증  → 400
   (락 전 1차 확인으로 빠른 실패, 락 후 2차 확인으로 최종 검증)
⑤ 상태에 따라 분기:
   PENDING/CONFIRMED
     → enrollment → CANCELLED, registration → CANCELLED, reservedCount--
   WAITLISTED
     → waitlist_entry → CANCELLED, registration → CANCELLED
⑥ [PENDING/CONFIRMED 취소 한정] WAITING 대기자 승격 처리:
     MIN(sequence)인 WAITING 대기자 1명 조회 (PESSIMISTIC_WRITE)
     있으면 → waitlist_entry → PROMOTED
              registration → PENDING
              enrollment 신규 생성(PENDING), reservedCount++
     없으면 → 종료
⑦ 커밋                                        락 해제
```

**대기 순번은 sequence를 감소시키지 않음. 취소 gap은 ROW_NUMBER()로 실시간 보정:**
```sql
SELECT *, ROW_NUMBER() OVER (PARTITION BY klass_id ORDER BY sequence ASC) AS position
FROM waitlist_entry
WHERE klass_id = ? AND status = 'WAITING';
```
API 응답의 `waitlistPosition`은 raw sequence가 아닌 ROW_NUMBER() 계산 결과를 반환한다.

---

## 대기자 조회 쿼리

인덱스 `(klass_id, status, sequence)` 활용:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<WaitlistEntry> findFirstByKlassIdAndStatusOrderBySequenceAsc(
    Long klassId, WaitlistStatus status);
```

---

## 락 적용 범위

| 기능 | klass 락 | enrollment/registration 락 | 이유 |
|------|---------|--------------------------|------|
| 수강 신청 | O | — | reservedCount 증가, waitlist_next_sequence 증가, 중복 신청 직렬화 |
| 결제 확정 | X | O (enrollment 행) | PENDING→CONFIRMED 상태 전이 보호 |
| 취소 (PENDING/CONFIRMED) | O | — | reservedCount 감소, 대기자 승격 (klass 락으로 직렬화) |
| 취소 (WAITLISTED) | O | — | 대기자 승격과 대기 취소 충돌 방지 |
| 강의 목록/상세 조회 | X | — | 읽기 전용 |
| 내 신청 목록 조회 | X | — | 읽기 전용 |

---

## 락 타임아웃 예외 처리

락 3초 대기 후 획득 실패 시 `LockTimeoutException` 발생.

```java
@ExceptionHandler(LockTimeoutException.class)
public ResponseEntity<Map<String, String>> handleLockTimeout(LockTimeoutException e) {
    return ResponseEntity.status(503).body(Map.of("error", "잠시 후 다시 시도해주세요."));
}
```

---

## 테스트 한계

```
MySQL FOR UPDATE → 실제 행 레벨 잠금, 다른 트랜잭션 대기
H2 FOR UPDATE   → 유사하게 동작하지만 완벽한 재현 보장 안 됨
```

동시성 테스트가 H2에서 통과해도 MySQL에서 다르게 동작할 수 있음.
가장 정확한 검증은 Testcontainers 기반 MySQL 통합 테스트이나,
과제 범위에서는 "정확한 검증은 MySQL 환경에서 수행해야 한다"는 README 명시로 대체한다.

---

## 설계 근거

본 프로젝트에서는 정원과 대기열 순번을 강의 단위의 공유 자원으로 보고,
klass 행에 대한 비관적 락을 사용해 수강 신청과 대기자 승격을 직렬화하였다.

수강 신청 시에는 klass 락을 획득한 트랜잭션 안에서 활성 신청 중복 여부를 확인하고,
reserved_count와 capacity를 비교한다. 자리가 있으면 enrollment를 PENDING 상태로 생성하고
reserved_count를 증가시키며, 자리가 없으면 waitlist_entry를 WAITING 상태로 생성하고
waitlist_next_sequence를 증가시킨다.

취소와 대기자 승격도 동일한 klass 락 안에서 처리한다. 이를 통해 수강생 취소로 생긴
자리에 신규 신청과 대기자 승격이 동시에 접근하거나, 대기자가 취소되는 동시에 승격되는
상황을 방지한다.

결제 확정은 reserved_count를 변경하지 않으므로 klass 락은 사용하지 않는다.
다만 registration과 enrollment의 상태 전이를 보호하기 위해 해당 enrollment 행을
비관적 락으로 조회한 뒤 PENDING 상태인지 검증하고 CONFIRMED로 변경한다.

DB 비관적 락은 여러 애플리케이션 서버가 동일한 DB primary를 사용하는 환경에서도 유효하다.
Redis 분산락은 DB 트랜잭션으로 보호되지 않는 외부 자원까지 함께 제어해야 하는 경우에 검토한다.

| 방식 | 선택 여부 | 이유 |
|------|---------|------|
| 비관적 락 | **선택** | 단일 DB, 충돌 시 명확한 순서 보장, 구현 단순 |
| 낙관적 락 | 미선택 | 충돌 시 재시도 로직 필요, 높은 경쟁 환경에서 비효율 |
| Redis 분산락 | 미선택 | 현재 구조에서 불필요 (분산 자원 없음) |
