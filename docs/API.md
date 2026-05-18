# LiveKlass API 명세서

## 1. 개요

이 API는 강의 등록/조회/상태 변경과 수강 신청/확정/취소/대기열을 처리한다.

기준 구현:

- Java 21 / Spring Boot 3.5
- 기본 Base URL: `http://localhost:8080`
- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`

## 2. 공통 규칙

### 2.1 인증 헤더

인증이 필요한 API는 다음 헤더를 요구한다.

| Header | 필수 | 값 | 설명 |
|---|---:|---|---|
| `X-User-Id` | O | 예: `1`, `100` | 요청 사용자 ID (숫자) |

### 2.2 에러 응답

에러 응답은 공통적으로 다음 형태를 사용한다.

```json
{
  "error": "에러 메시지"
}
```

주요 에러 코드:

| HTTP | 상황 |
|---:|---|
| 400 | 입력값 검증 실패, 잘못된 상태 전이, 취소 불가 상태, 취소 기간 초과 |
| 403 | 권한 없음 (타인 강의 수정, 타인 수강 내역 접근) |
| 404 | 강의 / 사용자 / 수강 신청 없음 |
| 409 | 중복 수강 신청 |
| 503 | 동시 요청 처리 중 잠금 충돌 — 잠시 후 재시도 |

### 2.3 페이지네이션

페이지네이션을 지원하는 API는 Spring Data Page 형태로 응답한다.

Query 파라미터:

| 이름 | 기본값 | 설명 |
|---|---|---|
| `page` | 0 | 0-based 페이지 번호 |
| `size` | API별 상이 | 페이지 크기 |
| `sort` | API별 상이 | 정렬 기준 |

응답 구조:

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

---

## 3. 강의 API

### 3.1 강의 등록

`POST /api/classes`

인증: 필요 (`X-User-Id` = 강사 ID)

요청:

```json
{
  "title": "Spring Boot 마스터",
  "description": "실전 스프링 부트 강의",
  "price": 99000,
  "capacity": 30,
  "startDate": "2026-06-01",
  "endDate": "2026-08-31"
}
```

요청 필드:

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `title` | string | O | 강의 제목, 공백 불가 |
| `description` | string | X | 강의 설명 |
| `price` | number | O | 수강료, 0 이상 |
| `capacity` | number | O | 최대 정원, 1 이상 |
| `startDate` | string | O | 수강 시작일 `yyyy-MM-dd` |
| `endDate` | string | O | 수강 종료일 `yyyy-MM-dd`, startDate 이후 |

응답 `201 Created`:

```json
{
  "id": 1,
  "creatorId": 1,
  "title": "Spring Boot 마스터",
  "description": "실전 스프링 부트 강의",
  "price": 99000.00,
  "capacity": 30,
  "reservedCount": 0,
  "availableSeats": 30,
  "startDate": "2026-06-01",
  "endDate": "2026-08-31",
  "status": "DRAFT",
  "createdAt": "2026-05-18T10:00:00",
  "updatedAt": "2026-05-18T10:00:00"
}
```

비즈니스 규칙:

- 생성 시 상태는 항상 `DRAFT`다.
- `startDate >= endDate`이면 `400 INVALID_DATE_RANGE`

---

### 3.2 강의 목록 조회

`GET /api/classes`

인증: 불필요

Query:

| 이름 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `status` | enum | X | `DRAFT` / `OPEN` / `CLOSED`. 생략 시 전체 조회 |

응답 `200 OK`:

```json
[
  {
    "id": 1,
    "creatorId": 1,
    "title": "Spring Boot 마스터",
    "description": "실전 스프링 부트 강의",
    "price": 99000.00,
    "capacity": 30,
    "reservedCount": 10,
    "availableSeats": 20,
    "startDate": "2026-06-01",
    "endDate": "2026-08-31",
    "status": "OPEN",
    "createdAt": "2026-05-18T10:00:00",
    "updatedAt": "2026-05-18T11:00:00"
  }
]
```

---

### 3.3 강의 상세 조회

`GET /api/classes/{id}`

인증: 불필요

Path:

| 변수 | 타입 | 설명 |
|---|---|---|
| `id` | number | 강의 PK |

응답 `200 OK`: 3.1 응답과 동일한 구조

에러:
- `404` 강의 없음

---

### 3.4 강의 상태 변경

`PATCH /api/classes/{id}/status`

인증: 필요 (`X-User-Id` = 강사 ID)

Path:

| 변수 | 타입 | 설명 |
|---|---|---|
| `id` | number | 강의 PK |

요청:

```json
{ "status": "OPEN" }
```

응답 `200 OK`: 3.1 응답과 동일한 구조

상태 전이 규칙:

| 현재 상태 | 변경 가능 상태 | 불가 시 에러 |
|---|---|---|
| DRAFT | OPEN | — |
| OPEN | CLOSED | DRAFT로 변경 불가 (`INVALID_STATUS_TRANSITION`) |
| CLOSED | 없음 | `KLASS_ALREADY_CLOSED` |

에러:
- `404` 강의 없음
- `403` 본인 강의 아님 (`KLASS_ACCESS_DENIED`)
- `400` 잘못된 상태 전이

---

### 3.5 강의별 수강생 목록 조회 (강사 전용)

`GET /api/classes/{id}/enrollments`

인증: 필요 (`X-User-Id` = 강사 ID)

Path:

| 변수 | 타입 | 설명 |
|---|---|---|
| `id` | number | 강의 PK |

Query: 페이지네이션 파라미터 지원 (기본 size=20)

응답 `200 OK` (Page):

```json
{
  "content": [
    {
      "enrollmentId": 1,
      "userId": 100,
      "status": "CONFIRMED",
      "waitlistPosition": null,
      "confirmedAt": "2026-05-18T11:00:00",
      "cancelDeadlineAt": "2026-05-25T11:00:00"
    },
    {
      "enrollmentId": 2,
      "userId": 101,
      "status": "WAITLISTED",
      "waitlistPosition": 1,
      "confirmedAt": null,
      "cancelDeadlineAt": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1
}
```

응답 필드:

| 필드 | 설명 |
|---|---|
| `enrollmentId` | 수강 신청 PK (`class_registration.id`) |
| `userId` | 수강생 ID |
| `status` | `PENDING` / `CONFIRMED` / `WAITLISTED` / `CANCELLED` |
| `waitlistPosition` | 대기 순번 (WAITLISTED인 경우만 값 존재) |
| `confirmedAt` | 결제 확정 시각 |
| `cancelDeadlineAt` | 취소 가능 기한 (결제 확정 후 7일) |

에러:
- `404` 강의 없음
- `403` 본인 강의 아님 (`ENROLLMENT_ACCESS_DENIED`)

---

## 4. 수강 신청 API

### 4.1 수강 신청

`POST /api/enrollments`

인증: 필요 (`X-User-Id` = 수강생 ID)

요청:

```json
{ "classId": 1 }
```

응답 `201 Created` — 자리 있음 (`PENDING`):

```json
{
  "registrationId": 1,
  "classId": 1,
  "classTitle": "Spring Boot 마스터",
  "userId": 100,
  "status": "PENDING",
  "waitlistPosition": null,
  "reservedAt": "2026-05-18T10:00:00",
  "confirmedAt": null,
  "cancelDeadlineAt": null,
  "createdAt": "2026-05-18T10:00:00",
  "updatedAt": "2026-05-18T10:00:00"
}
```

응답 `201 Created` — 정원 초과 시 대기열 (`WAITLISTED`):

```json
{
  "registrationId": 2,
  "classId": 1,
  "classTitle": "Spring Boot 마스터",
  "userId": 101,
  "status": "WAITLISTED",
  "waitlistPosition": 1,
  "reservedAt": null,
  "confirmedAt": null,
  "cancelDeadlineAt": null,
  "createdAt": "2026-05-18T10:00:01",
  "updatedAt": "2026-05-18T10:00:01"
}
```

비즈니스 규칙:

- 강의 상태가 `OPEN`이 아니면 `400 KLASS_NOT_OPEN`
- `PENDING` / `CONFIRMED` / `WAITLISTED` 상태의 신청이 이미 존재하면 `409 ENROLLMENT_DUPLICATE`
- 자리가 있으면 `reserved_count`를 증가시키고 `PENDING` + `enrollment` 생성
- 자리가 없으면 `WAITLISTED` + `waitlist_entry` 생성
- `waitlistPosition`은 `id` 기준 실시간 순위 계산. 중간 취소 후에도 항상 1부터 연속된 값을 반환한다.

에러:
- `404` 강의 없음 / 사용자 없음
- `400` OPEN 상태 아님
- `409` 중복 신청

---

### 4.2 결제 확정

`POST /api/enrollments/confirm`

인증: 필요 (`X-User-Id` = 수강생 ID)

요청:

```json
{ "classId": 1 }
```

응답 `200 OK`:

```json
{
  "registrationId": 1,
  "classId": 1,
  "classTitle": "Spring Boot 마스터",
  "userId": 100,
  "status": "CONFIRMED",
  "waitlistPosition": null,
  "reservedAt": "2026-05-18T10:00:00",
  "confirmedAt": "2026-05-18T11:00:00",
  "cancelDeadlineAt": "2026-05-25T11:00:00",
  "createdAt": "2026-05-18T10:00:00",
  "updatedAt": "2026-05-18T11:00:00"
}
```

비즈니스 규칙:

- `PENDING` 상태인 신청만 확정 가능. 그 외 상태는 `400 ENROLLMENT_NOT_PENDING`
- 확정 시 `cancelDeadlineAt = confirmedAt + 7일`로 계산해 저장

에러:
- `404` 활성 수강 신청 없음
- `400` PENDING 상태 아님

---

### 4.3 수강 취소

`POST /api/enrollments/cancel`

인증: 필요 (`X-User-Id` = 수강생 ID)

요청:

```json
{ "classId": 1 }
```

응답 `200 OK`:

```json
{
  "registrationId": 1,
  "classId": 1,
  "classTitle": "Spring Boot 마스터",
  "userId": 100,
  "status": "CANCELLED",
  "waitlistPosition": null,
  "reservedAt": "2026-05-18T10:00:00",
  "confirmedAt": "2026-05-18T11:00:00",
  "cancelDeadlineAt": "2026-05-25T11:00:00",
  "createdAt": "2026-05-18T10:00:00",
  "updatedAt": "2026-05-18T12:00:00"
}
```

비즈니스 규칙:

- `PENDING` 취소: `enrollment` 취소, `reserved_count` 감소, 대기열 첫 번째 자동 승격
- `CONFIRMED` 취소: `cancelDeadlineAt` 초과 시 `400 CANCEL_PERIOD_EXPIRED`. 기한 내면 위와 동일
- `WAITLISTED` 취소: `waitlist_entry`만 취소, `reserved_count` 변동 없음
- 취소된 자리가 생기면 `waitlist_entry.id ASC` 순서로 첫 번째 대기자를 자동으로 `PENDING`으로 승격한다.

에러:
- `404` 활성 수강 신청 없음
- `400` 취소 가능 기간 초과 / 이미 취소된 상태

---

### 4.4 내 수강 신청 목록

`GET /api/enrollments/me`

인증: 필요 (`X-User-Id` = 수강생 ID)

Query: 페이지네이션 파라미터 지원 (기본 size=10, sort=createdAt DESC)

응답 `200 OK` (Page):

```json
{
  "content": [
    {
      "registrationId": 1,
      "classId": 1,
      "classTitle": "Spring Boot 마스터",
      "userId": 100,
      "status": "CONFIRMED",
      "waitlistPosition": null,
      "reservedAt": "2026-05-18T10:00:00",
      "confirmedAt": "2026-05-18T11:00:00",
      "cancelDeadlineAt": "2026-05-25T11:00:00",
      "createdAt": "2026-05-18T10:00:00",
      "updatedAt": "2026-05-18T11:00:00"
    },
    {
      "registrationId": 2,
      "classId": 2,
      "classTitle": "Java 기초",
      "userId": 100,
      "status": "WAITLISTED",
      "waitlistPosition": 3,
      "reservedAt": null,
      "confirmedAt": null,
      "cancelDeadlineAt": null,
      "createdAt": "2026-05-18T11:00:00",
      "updatedAt": "2026-05-18T11:00:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 2,
  "totalPages": 1
}
```
