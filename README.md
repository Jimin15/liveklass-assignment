# LiveKlass — 수강 신청 시스템

---

## 프로젝트 개요

크리에이터가 강의를 개설하고, 수강생이 신청·확정·취소하는 수강 신청 백엔드 시스템입니다.
비관적 락으로 동시 신청 경쟁을 처리하고, 정원 초과 시 대기열을 운영합니다.

---

## 구현 범위

### 필수 구현

- [x] 강의 등록 (제목, 설명, 가격, 정원, 수강 기간)
- [x] 강의 상태 관리 (DRAFT → OPEN → CLOSED)
- [x] 강의 목록 조회 (상태 필터)
- [x] 강의 상세 조회 (현재 신청 인원 포함)
- [x] 수강 신청 (PENDING → CONFIRMED → CANCELLED)
- [x] 결제 확정 처리
- [x] 수강 취소
- [x] 내 수강 신청 목록 조회
- [x] 정원 초과 신청 거부
- [x] 동시 신청 경쟁 조건 처리 (비관적 락)

### 선택 구현

- [x] 취소 가능 기간 제한 (결제 확정 후 7일 이내)
- [x] 대기열(Waitlist) 기능 — 정원 초과 시 자동 대기 등록, 취소 시 자동 승격
- [x] 강의별 수강생 목록 조회 (크리에이터 전용)
- [x] 신청 내역 페이지네이션

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.15-SNAPSHOT |
| ORM | Spring Data JPA (Hibernate 6) |
| DB (운영) | MySQL 8 (Docker) |
| DB (테스트) | H2 In-Memory (MODE=MySQL) |
| 동시성 제어 | JPA Pessimistic Write Lock (SELECT FOR UPDATE) |
| API 문서 | SpringDoc OpenAPI (Swagger UI) |
| 빌드 도구 | Gradle |

---

## 실행 방법

### 1. Docker로 MySQL 실행

```bash
docker compose up -d
```

MySQL이 준비될 때까지 약 10초 대기 후 앱을 실행합니다.

docker-compose.yml 설정 기준:

| 항목 | 값 |
|------|----|
| 이미지 | mysql:8 |
| 포트 | 3307:3306 |
| DB명 | liveklass |
| 계정 | liveklass / liveklass1234 |

### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

- 기본 포트: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

### 3. 테스트 실행

```bash
./gradlew test
```

H2 In-Memory DB를 사용하므로 별도 DB 설정 없이 실행됩니다.

---

## 요구사항 해석 및 가정

수강 신청은 좌석 점유 여부에 따라 두 경로로 분기됩니다. 자리가 있으면 `enrollment`를 생성하고 `PENDING` 상태로 좌석을 점유하며, 정원 초과 시에는 거부 대신 `waitlist_entry`를 생성하고 `WAITLISTED` 상태로 대기열에 등록합니다. 결제 확정(`confirm`)은 `PENDING → CONFIRMED` 전이이며, 취소는 세 상태(PENDING, CONFIRMED, WAITLISTED) 모두에서 가능합니다.

취소 가능 기한(7일)은 CONFIRMED 상태에만 적용합니다. PENDING 취소는 기간 제한 없이 허용하며, WAITLISTED 취소도 언제든지 가능합니다. 기한은 결제 확정 시점(`confirmedAt`)에 `+7일`로 미리 계산해 `cancel_deadline_at` 컬럼에 저장합니다. 취소 시점이 아닌 확정 시점에 기산하므로 이후 상태 변화와 무관하게 기한이 확정됩니다.

CANCELLED 후 동일 강의 재신청은 허용합니다. 중복 신청 체크는 PENDING / CONFIRMED / WAITLISTED 상태만 확인하므로, 취소 이력이 있어도 새로운 신청이 가능합니다.

대기열 순서는 `waitlist_entry.id` (auto increment) 기준으로 결정합니다. 삽입 순서가 곧 대기 순서이므로 별도 sequence 컬럼이 필요 없습니다. 대기 순번(`waitlistPosition`)은 `COUNT(id < 내 id AND status = WAITING) + 1`로 실시간 계산합니다. 중간 취소로 gap이 생겨도 WAITING 항목만 세므로 항상 1부터 연속된 값을 반환합니다.

인증은 `X-User-Id` 헤더로 userId를 직접 전달하는 방식으로 간소화했습니다. 실제 서비스라면 인증 서버가 JWT를 검증한 뒤 해당 헤더를 주입한다고 가정합니다.

---

## 설계 결정과 이유

### 수강 신청 테이블 구조

수강 신청을 단일 테이블로 관리하지 않고 `class_registration`, `enrollment`, `waitlist_entry` 세 테이블로 분리했습니다. `class_registration`은 집계 루트로서 한 사용자의 한 강의에 대한 모든 신청 이력을 담습니다. `enrollment`와 `waitlist_entry`는 자리 확보 여부에 따라 생성되는 위성 테이블로, 각각 다른 생명주기와 데이터를 갖습니다.

`enrollment`와 `waitlist_entry`를 하나로 합치지 않은 이유는 두 상태가 관리하는 정보가 다르기 때문입니다. `enrollment`는 결제 확정 시각, 취소 기한 등 좌석 점유에 관한 정보를 가지며, `waitlist_entry`는 대기 순서 결정에 필요한 정보를 가집니다. 하나의 테이블에 두 상태를 모두 담으면 NULL 컬럼이 늘어나고 어느 상태인지에 따라 유효한 컬럼이 달라져 테이블 의미가 불명확해집니다.

`enrollment`를 결제 정보 테이블로 더 분리하는 방안도 고려했으나, 과제 범위에서 결제는 단순 확정/취소만 처리하므로 분리의 실익이 없다고 판단해 `enrollment` 안에서 관리합니다.

### 동시성 제어 — 비관적 락 ([상세 문서](docs/CONCURRENCY.md))

수강 신청과 취소는 `klass` 행에 `SELECT FOR UPDATE` 락을 걸어 요청을 직렬화합니다.

**Redis 분산 락을 선택하지 않은 이유**: 본 시스템은 단일 애플리케이션 서버, 단일 DB 인스턴스를 가정합니다. Redis 분산 락은 여러 서버 인스턴스가 DB 외부의 공유 자원(캐시, 외부 API 등)을 함께 제어해야 할 때 필요합니다. 현재 구조에서는 DB 비관적 락만으로 충분히 원자성을 보장할 수 있으며, Redis 인프라를 별도로 운영하는 비용 대비 실익이 없습니다. 사용자 수가 많지 않다고 가정하므로, SELECT FOR UPDATE로 인한 직렬화 비용이 허용 가능한 수준입니다.

**낙관적 락을 선택하지 않은 이유**: 낙관적 락은 "충돌이 거의 발생하지 않는다"는 가정 하에, 충돌이 발생했을 때 감지하고 재시도하는 후처리 방식입니다. 반면 수강 신청은 특정 강의의 마지막 자리를 두고 다수가 동시에 경쟁하는 상황이 충분히 예상됩니다. 충돌이 발생할 것을 가정하고 선점하는 비관적 락이 이 도메인의 특성에 더 맞습니다. 낙관적 락으로 구현했다면 경쟁이 몰리는 순간 다수의 요청이 반복 실패와 재시도를 반복해 오히려 처리량이 낮아질 수 있습니다.

결제 확정(`confirm`)은 `reserved_count`를 변경하지 않으므로 klass 락이 불필요합니다. 대신 같은 `enrollment` 행에 확정과 취소가 동시에 들어오는 경우를 막기 위해 `enrollment` 행만 락을 겁니다. 이렇게 하면 확정 요청이 klass 락을 두고 신청/취소 요청과 경합하는 상황을 방지할 수 있습니다.

### 취소 이중 체크 패턴

CONFIRMED 취소 시 취소 기한 검증을 락 전후 두 번 수행합니다. 락 전 1차 확인은 기한이 명백히 초과된 요청을 빠르게 거절해 불필요한 락 경합을 줄입니다. 락 후 2차 확인은 락 획득 시점에 DB에서 최신 데이터를 다시 읽어 최종 검증합니다. 1차 확인만으로는 락 대기 중 기한이 만료되는 TOCTOU 문제가 발생할 수 있습니다.

### `reserved_count` klass에 저장

현재 신청 인원(`reserved_count`)은 `class_registration` 테이블에서 매번 `COUNT` 쿼리로 계산할 수 있지만, `klass` 테이블에 컬럼으로 직접 저장합니다. 정원 확인은 수강 신청마다 발생하는 빈번한 연산인데, COUNT 쿼리 대신 `klass` 행 하나를 읽는 것으로 해결됩니다. 단, 신청/취소/승격 시마다 직접 +1/-1 동기화해야 하므로 반드시 `SELECT FOR UPDATE` 락 안에서 처리해 정합성을 보장합니다.

### `cancel_deadline_at` 확정 시점에 미리 계산

취소 가능 기한을 취소 요청 시점이 아닌, 결제 확정 시점에 `confirmedAt + 7일`로 계산해 `enrollment.cancel_deadline_at`에 저장합니다. 취소 요청마다 계산하는 방식은 `confirmedAt` 기준 로직이 바뀌면 과거 건에도 영향을 줄 수 있습니다. 확정 시점에 한 번 저장해두면 이후 로직이 변경되더라도 기존 건의 기한은 고정되어 안전합니다. 취소 시 단순 컬럼 비교만으로 기한 초과 여부를 판단할 수 있어 쿼리도 단순해집니다.

### `waitlist_entry.id` 기반 대기 순서

대기 순서를 관리하기 위해 별도 `sequence` 컬럼을 두지 않고 `waitlist_entry.id` (auto increment)를 활용합니다. 락 안에서 순서대로 삽입되므로 삽입 순서 = id 순서 = 대기 순서가 보장됩니다. 대기 순번은 `COUNT(id < 내 id AND status = WAITING) + 1`로 계산하며, 중간 취소로 gap이 생겨도 WAITING 항목만 세므로 항상 연속된 순번을 반환합니다. 취소 시 나머지 대기자의 sequence를 일괄 업데이트하는 비용이 없어 쓰기 경합도 줄어듭니다.

---

## 미구현 / 제약사항

인증은 `X-User-Id` 헤더로 간소화했습니다. 실제 서비스라면 게이트웨이 또는 인증 미들웨어가 JWT를 검증한 뒤 해당 헤더를 주입해야 하며, 헤더를 클라이언트가 직접 조작할 수 없도록 보호해야 합니다.

중복 신청 방지는 애플리케이션 레벨 검사에만 의존합니다. 현재 구현에서는 비관적 락 안에서 중복 여부를 확인하므로 대부분의 경우 안전하지만, DB 레벨 안전장치가 없습니다. 실서비스라면 `PENDING/CONFIRMED/WAITLISTED` 상태일 때만 유니크 제약이 동작하는 조건부 유니크 인덱스(`active_key` 컬럼 + `NULL` 중복 허용)를 추가해 이중 방어를 구성하는 것이 안전합니다.

동시성 테스트는 H2 in-memory DB로 실행됩니다. H2의 `SELECT FOR UPDATE`는 MySQL과 완전히 동일하게 동작하지 않으므로 H2에서 통과하더라도 MySQL 환경에서 동작을 보장하지 않습니다. 정확한 검증은 Testcontainers 기반 MySQL 통합 테스트로 수행해야 합니다.

목록 조회 API(`getMyEnrollments`, `getEnrollmentsByKlass`)에 N+1 문제가 있습니다. 현재는 `class_registration` 목록을 조회한 뒤 각 건마다 `enrollment`와 `klass`를 별도로 조회합니다. 실서비스에서 건수가 많아지면 `IN` 쿼리로 배치 로딩하거나 JOIN으로 개선이 필요합니다.

비관적 락은 단일 DB 인스턴스에서만 유효합니다. 다중 애플리케이션 서버 환경에서는 동일한 DB primary를 공유하는 한 유효하지만, DB를 샤딩하거나 외부 자원을 함께 제어해야 하는 경우에는 Redis 분산 락으로 전환을 검토해야 합니다.

---

## AI 활용 범위

본 과제는 Claude Code와 OpenAI Codex를 활용하여 개발하였습니다.

구현에 앞서 요구사항을 직접 분석하고 전체 개발 계획(Plan)을 수립했습니다. 동시성 처리 방식, 대기열 설계, 테이블 구조 등 핵심 설계 결정은 직접 내리고, 코드 작성 전에 별도 문서(`docs/CONCURRENCY.md`, `docs/ERD.md`)로 정리했습니다.

구현은 Claude와 Codex를 교차 검증하는 방식으로 활용했습니다. 설계 의도에 맞게 코드를 작성하되, 한 도구가 생성한 코드를 다른 도구로 재검토하여 누락되거나 잘못된 부분을 찾아냈습니다. 이 과정에서 비관적 락 이중 체크 패턴 누락, 락 타임아웃 미설정, double-cancel 시 `reservedCount` 오염, 불필요한 `waitlist sequence` 컬럼 등의 문제를 발견하고 수정했습니다.

Claude Code의 전문화 스킬도 활용했습니다. `code-reviewer`로 코드 품질 이슈를 점검하고, `architect`로 설계 결정을 검토하며, `test-engineer`로 테스트 케이스를 보강했습니다.

AI가 생성한 코드를 그대로 사용하지 않고, 직접 검토 → 오류 수정 → 테스트 검증의 과정을 거쳤습니다.

---

## API 목록 및 예시

자세한 명세는 [`docs/API.md`](docs/API.md)를 참고합니다.

### 강의 API

| Method | URL | 설명 | 인증 | 응답 코드 |
|--------|-----|------|------|-----------|
| POST | `/api/classes` | 강의 생성 | 필요 | 201 |
| GET | `/api/classes` | 강의 목록 (`?status=OPEN` 선택) | 불필요 | 200 |
| GET | `/api/classes/{id}` | 강의 단건 조회 | 불필요 | 200 |
| PATCH | `/api/classes/{id}/status` | 강의 상태 변경 (크리에이터 전용) | 필요 | 200 |
| GET | `/api/classes/{id}/enrollments` | 강의별 수강생 목록 (크리에이터 전용) | 필요 | 200 |

### 수강 신청 API

| Method | URL | 설명 | 인증 | 응답 코드 |
|--------|-----|------|------|-----------|
| POST | `/api/enrollments` | 수강 신청 | 필요 | 201 |
| POST | `/api/enrollments/confirm` | 결제 확정 | 필요 | 200 |
| POST | `/api/enrollments/cancel` | 수강 취소 | 필요 | 200 |
| GET | `/api/enrollments/me` | 내 수강 목록 (페이지네이션) | 필요 | 200 |

### 수강 신청 예시

```bash
# 수강 신청
curl -X POST http://localhost:8080/api/enrollments \
  -H "X-User-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{"classId": 1}'

# 결제 확정
curl -X POST http://localhost:8080/api/enrollments/confirm \
  -H "X-User-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{"classId": 1}'

# 수강 취소
curl -X POST http://localhost:8080/api/enrollments/cancel \
  -H "X-User-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{"classId": 1}'
```

---

## 데이터 모델 설명

자세한 ERD는 [`docs/ERD.md`](docs/ERD.md)를 참고합니다.

### 핵심 설계

수강 신청을 3개 테이블로 분리하여 좌석 점유 상태와 대기 상태를 각각 관리합니다.

```
class_registration  (집계 루트 — 모든 신청 이력)
  ├── enrollment     (자리 확보 신청 — PENDING / CONFIRMED / CANCELLED)
  └── waitlist_entry (대기 신청 — WAITING / PROMOTED / CANCELLED)
```

### 수강 상태 흐름

```
신청 → PENDING    (자리 있음)
신청 → WAITLISTED (정원 초과)

PENDING    → CONFIRMED  (결제 확정)
PENDING    → CANCELLED  (취소, 기간 제한 없음)
CONFIRMED  → CANCELLED  (취소, 확정 후 7일 이내)
WAITLISTED → PENDING    (대기자 자동 승격)
WAITLISTED → CANCELLED  (대기 포기)
```

### 정원 관리

- `klass.reserved_count` = PENDING + CONFIRMED 인원 합계
- 수강 신청/취소/승격 시 `SELECT FOR UPDATE`로 klass 행을 잠가 원자적으로 처리

### 대기열 순서

- `waitlist_entry.id` (auto increment) ASC 순서가 대기 순서
- 별도 sequence 컬럼 없이 삽입 순서 = 대기 순서
- `waitlistPosition` = `COUNT(id < 내 id AND status = WAITING) + 1` (실시간 계산, gap 자동 보정)

---

## 테스트 실행 방법

```bash
# 전체 테스트 실행 (H2, Docker 불필요)
./gradlew test

# 특정 클래스만 실행
./gradlew test --tests "com.example.liveklass.enrollment.EnrollmentServiceTest"
./gradlew test --tests "com.example.liveklass.enrollment.EnrollmentConcurrencyTest"
```

### 테스트 구성

| 클래스 | 종류 | 주요 케이스 |
|--------|------|------------|
| `EnrollmentServiceTest` | 단위 테스트 (Mockito) | 신청(자리 있음/없음/중복/사용자 없음), 확정(성공/PENDING 아님/enrollment 상태/deadline 7일), 취소(PENDING/CONFIRMED/WAITLISTED/기한 초과/기한 null/이미 취소/대기자 승격), 목록 조회(position 반환/권한) |
| `KlassServiceTest` | 단위 테스트 (Mockito) | 강의 생성/조회/상태 변경 비즈니스 로직 검증 |
| `EnrollmentConcurrencyTest` | 동시성 통합 테스트 (`@SpringBootTest`) | 동시 신청 시 정원 초과 없음, reservedCount 정합성 |
| `LiveklassApplicationTests` | 컨텍스트 로드 테스트 | 스프링 컨텍스트 정상 로드 확인 |

### 동시성 테스트 주의사항

동시성 테스트는 H2 in-memory DB를 사용합니다.
H2의 `SELECT FOR UPDATE`는 MySQL과 동작 방식이 완전히 동일하지 않으므로, 정확한 검증은 MySQL 환경(Testcontainers 등)에서 수행해야 합니다.
