# LiveKlass ERD 문서

## 1. 개요

이 문서는 현재 JPA 엔티티 기준의 데이터 모델을 설명한다.

핵심 설계:

- 모든 테이블은 DB auto increment Long PK를 사용한다.
- 수강 신청의 집계 루트는 `class_registration`이며, 자리 확보 여부에 따라 `enrollment` 또는 `waitlist_entry`가 생성된다.
- 대기 순서는 `waitlist_entry.id` (auto increment) 기준으로 결정된다. 삽입 순서 = 대기 순서이므로 별도 sequence 컬럼이 필요하지 않다.
- 정원 관리는 `klass.reserved_count`로 하며, 자리 변동 시 `SELECT FOR UPDATE` 락으로 원자적으로 처리한다.
- 취소 가능 기한은 결제 확정 시점에 `confirmed_at + 7일`로 미리 계산해 저장한다.

## 2. ERD

![ERD](erd.png)

## 3. 테이블 상세

### 3.1 app_user

사용자 계정 마스터.

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | bigint | PK, auto increment | 내부 PK |
| name | varchar | not null | 사용자 이름 |
| email | varchar | not null, unique | 이메일 |

관계:

- app_user 1:N klass
- app_user 1:N class_registration

### 3.2 klass

강의 마스터.

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | bigint | PK, auto increment | 내부 PK |
| creator_id | bigint | not null, FK → app_user.id | 강사 |
| title | varchar | not null | 강의 제목 |
| description | TEXT | | 강의 설명 |
| price | decimal(10,2) | not null | 수강료 |
| capacity | int | not null | 최대 정원 |
| reserved_count | int | not null, default 0 | 자리 확보 인원 (PENDING + CONFIRMED) |
| status | enum | not null, default 'DRAFT' | DRAFT / OPEN / CLOSED |
| start_date | date | not null | 수강 시작일 |
| end_date | date | not null | 수강 종료일 |
| created_at | datetime | not null | |
| updated_at | datetime | not null | |

관계:

- klass N:1 app_user
- klass 1:N class_registration

### 3.3 class_registration

수강 신청 집계 루트. 한 사용자의 한 강의에 대한 신청 이력.

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | bigint | PK, auto increment | 내부 PK |
| klass_id | bigint | not null, FK → klass.id | |
| user_id | bigint | not null, FK → app_user.id | |
| status | enum | not null | PENDING / CONFIRMED / WAITLISTED / CANCELLED |
| created_at | datetime | not null | 신청 시각 |
| updated_at | datetime | not null | |
| cancelled_at | datetime | | 취소 시각 |

관계:

- class_registration N:1 klass
- class_registration N:1 app_user
- class_registration 1:0..1 enrollment
- class_registration 1:0..1 waitlist_entry

### 3.4 enrollment

자리가 확보된 신청의 결제/취소 상태 관리.

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | bigint | PK, auto increment | 내부 PK |
| registration_id | bigint | not null, unique, FK → class_registration.id | |
| status | enum | not null | PENDING / CONFIRMED / CANCELLED |
| reserved_at | datetime | not null | 자리 확보 시각 |
| confirmed_at | datetime | | 결제 확정 시각 |
| cancelled_at | datetime | | 취소 시각 |
| cancel_deadline_at | datetime | | 취소 가능 기한 (confirmed_at + 7일) |
| updated_at | datetime | not null | |

관계:

- enrollment N:1 class_registration

### 3.5 waitlist_entry

정원 초과 시 대기 상태 관리. `id` ASC 순서가 대기 순서.

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | bigint | PK, auto increment | 내부 PK이자 대기 순서 기준 |
| registration_id | bigint | not null, unique, FK → class_registration.id | |
| klass_id | bigint | not null | 조회 성능을 위해 중복 저장 |
| user_id | bigint | not null | 조회 성능을 위해 중복 저장 |
| status | enum | not null | WAITING / PROMOTED / CANCELLED |
| created_at | datetime | not null | |
| updated_at | datetime | not null | |
| promoted_at | datetime | | 승격 시각 |
| cancelled_at | datetime | | 취소 시각 |

관계:

- waitlist_entry N:1 class_registration
