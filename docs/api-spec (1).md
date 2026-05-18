# 크리에이터 정산 API 명세서

## 1. 개요

이 API는 강의 판매/환불 원장을 등록하고, 크리에이터별 월 정산 금액을 계산하거나 관리자용 정산 스냅샷을 생성/관리한다.

기준 구현:

- Java 21 / Spring Boot 3.5
- 기본 Base URL: `http://localhost:8080`
- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`

## 2. 공통 규칙

### 2.1 인증 헤더

모든 업무 API는 다음 헤더를 요구한다.

| Header | 필수 | 값 | 설명 |
|---|---:|---|---|
| `X-User-Id` | O | 예: `admin`, `creator-1` | 요청 사용자 ID |
| `X-User-Role` | O | `ADMIN`, `CREATOR` | 요청 사용자 역할 |

권한 규칙:

- `ADMIN`: 모든 관리자 API와 전체 크리에이터 데이터 접근 가능
- `CREATOR`: 본인 ID와 일치하는 크리에이터 데이터만 접근 가능
- 판매 등록은 `ADMIN` 전체 허용, `CREATOR`는 본인 강의에 한해 허용
- 환불 등록, 수수료 정책, 정산 스냅샷, 관리자 집계 API는 `ADMIN` 전용

### 2.2 시간과 금액

| 항목 | 규칙 |
|---|---|
| 시간 형식 | ISO-8601 Instant 또는 offset date-time |
| 저장/응답 시간대 | UTC |
| 월 정산 기준 | KST 월 경계 |
| 금액 | 원 단위 정수, 소수 불가 |
| 수수료율 | `0.20`은 20% |

### 2.3 식별자 정책

| 리소스 | API 식별자 |
|---|---|
| `Creator` | `loginId`, 예: `creator-1` |
| `Course` | `code`, 예: `course-1` |
| `SaleRecord` | DB 생성 숫자 PK |
| `Cancellation` | DB 생성 숫자 PK |
| `FeePolicy` | DB 생성 숫자 PK |
| `MonthlySettlement` | DB 생성 숫자 PK |

판매/환불 등록 요청에서는 원장 ID를 받지 않는다.

### 2.4 에러 응답

에러 응답은 공통적으로 다음 형태를 사용한다.

```json
{
  "code": "FORBIDDEN",
  "message": "권한이 없습니다."
}
```

주요 에러 코드:

| HTTP | code | 의미 |
|---:|---|---|
| 400 | `INVALID_REQUEST` | 요청 형식 또는 값 검증 실패 |
| 401 | `UNAUTHORIZED` | 인증 헤더 누락 |
| 403 | `FORBIDDEN` | 권한 없음 |
| 404 | `RESOURCE_NOT_FOUND` | 대상 리소스 없음 |
| 409 | `SETTLEMENT_ALREADY_EXISTS` | 같은 크리에이터/월 정산 스냅샷 중복 |
| 409 | `FEE_POLICY_DUPLICATE_EFFECTIVE_FROM` | 같은 적용 시작 시각의 수수료 정책 중복 |
| 409 | `INVALID_STATE_TRANSITION` | 허용되지 않는 정산 상태 전이 |
| 422 | `INVALID_PAYMENT_DATE` | 결제 시각이 미래 |
| 422 | `INVALID_CANCEL_DATE` | 환불 시각이 원판매 시각보다 이전 |
| 422 | `REFUND_EXCEEDS_SALE` | 환불 누적액이 원판매액 초과 |

## 3. 판매/환불 원장 API

### 3.1 판매 등록

`POST /api/sales`

권한:

- `ADMIN`: 모든 강의 판매 등록 가능
- `CREATOR`: 본인 강의 판매만 등록 가능

요청:

```json
{
  "courseId": "course-1",
  "studentId": "student-1",
  "amount": 50000,
  "paidAt": "2025-03-05T10:00:00+09:00"
}
```

요청 필드:

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `courseId` | string | O | 강의 외부 코드 |
| `studentId` | string | O | 수강생 ID |
| `amount` | number | O | 결제 금액, 원 단위 정수 |
| `paidAt` | string | O | 결제 시각 |

응답 `201 Created`:

```json
{
  "id": 1,
  "courseId": "course-1",
  "creatorId": "creator-1",
  "studentId": "student-1",
  "amount": 50000,
  "paidAt": "2025-03-05T01:00:00Z",
  "feeRateSnapshot": 0.20
}
```

비즈니스 규칙:

- `paidAt`이 미래이면 `INVALID_PAYMENT_DATE`
- `amount`는 양수 정수여야 한다.
- 수수료 정책은 `paidAt` 기준으로 자동 선택된다.
- 선택된 수수료율은 `feeRateSnapshot`으로 판매 원장에 저장된다.

### 3.2 환불 등록

`POST /api/sales/{saleId}/cancellations`

권한: `ADMIN`

Path:

| 변수 | 타입 | 설명 |
|---|---|---|
| `saleId` | number | 원판매 PK |

요청:

```json
{
  "refundAmount": 30000,
  "cancelledAt": "2025-03-24T00:00:00Z"
}
```

요청 필드:

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `refundAmount` | number | O | 환불 금액, 원 단위 정수 |
| `cancelledAt` | string | O | 환불 발생 시각 |

응답 `201 Created`:

```json
{
  "id": 2,
  "saleId": 1,
  "creatorId": "creator-1",
  "refundAmount": 30000,
  "cancelledAt": "2025-03-24T00:00:00Z",
  "feeRateSnapshot": 0.20
}
```

비즈니스 규칙:

- 원판매가 없으면 `RESOURCE_NOT_FOUND`
- `cancelledAt < paidAt`이면 `INVALID_CANCEL_DATE`
- 동일 판매의 환불 누적액이 원판매액을 초과하면 `REFUND_EXCEEDS_SALE`
- 환불 수수료율은 원판매의 `feeRateSnapshot`을 그대로 승계한다.

### 3.3 판매 목록 조회

`GET /api/sales?creatorId={creatorId}&from={from}&to={to}`

권한:

- `ADMIN`: 모든 `creatorId` 조회 가능
- `CREATOR`: 본인 `creatorId`만 조회 가능

Query:

| 이름 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `creatorId` | string | O | 크리에이터 ID |
| `from` | string | X | 조회 시작 시각. 생략 시 `Instant.EPOCH` |
| `to` | string | X | 조회 종료 시각. 생략 시 충분히 먼 미래 |

응답 `200 OK`:

```json
{
  "items": [
    {
      "id": 1,
      "courseId": "course-1",
      "creatorId": "creator-1",
      "studentId": "student-1",
      "amount": 50000,
      "paidAt": "2025-03-05T01:00:00Z",
      "feeRateSnapshot": 0.20
    }
  ],
  "total": 1
}
```

## 4. 수수료 정책 API

### 4.1 수수료 정책 목록 조회

`GET /api/admin/fee-policies`

권한: `ADMIN`

응답 `200 OK`:

```json
{
  "items": [
    {
      "id": 1,
      "rate": 0.20,
      "effectiveFrom": "2024-01-01T00:00:00Z"
    }
  ],
  "total": 1
}
```

### 4.2 수수료 정책 생성

`POST /api/admin/fee-policies`

권한: `ADMIN`

요청:

```json
{
  "rate": "0.18",
  "effectiveFrom": "2025-04-01T00:00:00Z"
}
```

요청 필드:

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `rate` | number/string | O | `0.0` 이상 `1.0` 이하 |
| `effectiveFrom` | string | O | 정책 적용 시작 시각 |

응답 `201 Created`:

```json
{
  "id": 2,
  "rate": 0.18,
  "effectiveFrom": "2025-04-01T00:00:00Z"
}
```

비즈니스 규칙:

- `effectiveFrom`이 같은 정책은 중복 생성할 수 없다.
- 판매 등록 시 별도 정책 ID를 넘기지 않는다. `paidAt` 기준 최신 정책을 자동 조회한다.

## 5. 크리에이터 월 정산 API

### 5.1 크리에이터 월 정산 조회

`GET /api/creators/{creatorId}/settlements/{yearMonth}`

권한:

- `ADMIN`: 모든 크리에이터 조회 가능
- `CREATOR`: 본인만 조회 가능

Path:

| 변수 | 타입 | 설명 |
|---|---|---|
| `creatorId` | string | 크리에이터 ID |
| `yearMonth` | string | `yyyy-MM` |

응답 `200 OK`:

```json
{
  "creatorId": "creator-1",
  "yearMonth": "2025-03",
  "totalSaleAmount": 260000,
  "totalRefundAmount": 110000,
  "netSaleAmount": 150000,
  "totalFeeAmount": 30000,
  "payoutAmount": 120000,
  "saleCount": 4,
  "cancellationCount": 2,
  "feeBreakdown": [
    {
      "rate": 0.20,
      "baseAmount": 150000,
      "feeAmount": 30000
    }
  ]
}
```

계산 규칙:

- KST 기준 `yearMonth` 월 경계로 판매 `paidAt`, 환불 `cancelledAt`을 집계한다.
- `netSaleAmount = totalSaleAmount - totalRefundAmount`
- `totalFeeAmount = 수수료율별 baseAmount * rate` 합계
- `payoutAmount = netSaleAmount - totalFeeAmount`
- 데이터가 없으면 404가 아니라 0 합계 응답을 반환한다.

## 6. 관리자 정산 집계 API

### 6.1 기간 정산 집계 조회

`GET /api/admin/settlement-summaries?from={from}&to={to}&format={format}`

권한: `ADMIN`

Query:

| 이름 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `from` | string | O | 조회 시작 시각 |
| `to` | string | O | 조회 종료 시각 |
| `format` | string | X | `csv` 지정 시 CSV 응답 |

JSON 응답 `200 OK`:

```json
{
  "items": [
    {
      "creatorId": "creator-1",
      "totalSale": 260000,
      "totalRefund": 110000,
      "netSale": 150000,
      "fee": 30000,
      "payout": 120000
    }
  ],
  "grandTotal": {
    "creatorId": "TOTAL",
    "totalSale": 260000,
    "totalRefund": 110000,
    "netSale": 150000,
    "fee": 30000,
    "payout": 120000
  }
}
```

CSV 응답:

```csv
creatorId,totalSale,totalRefund,fee,payout
creator-1,260000,110000,30000,120000
TOTAL,260000,110000,30000,120000
```

## 7. 관리자 정산 스냅샷 API

정산 스냅샷은 특정 크리에이터와 대상 월의 계산 결과를 저장하고, 포함된 판매/환불 원장에 `settlement_id`를 연결한다.

상태 흐름:

```text
PENDING -> CONFIRMED -> PAID
```

### 7.1 월 정산 스냅샷 생성

`POST /api/admin/settlements`

권한: `ADMIN`

요청:

```json
{
  "creatorId": "creator-1",
  "yearMonth": "2025-03"
}
```

응답 `201 Created`:

```json
{
  "id": 1,
  "creatorId": "creator-1",
  "yearMonth": "2025-03",
  "status": "PENDING",
  "snapshotAt": "2025-04-29T13:00:00Z",
  "totalSale": 260000,
  "totalRefund": 110000,
  "netSale": 150000,
  "totalFeeAmount": 30000,
  "payout": 120000,
  "lateArrivalSaleAmount": 0,
  "lateArrivalRefundAmount": 0,
  "lateArrivalFeeAmount": 0,
  "lateArrivalPayoutAmount": 0,
  "totalPayableAmount": 120000
}
```

비즈니스 규칙:

- 같은 `creatorId + yearMonth` 조합은 하나만 생성 가능하다.
- 이미 다른 정산에 포함된 원장은 중복 포함하지 않는다.
- 대상 월 이전 거래 중 아직 정산되지 않은 원장은 지각 도착 거래로 분리 집계한다.

### 7.2 월 정산 스냅샷 단건 조회

`GET /api/admin/settlements/{id}`

권한: `ADMIN`

응답: `MonthlySettlementResponse`

### 7.3 월 정산 스냅샷 목록 조회

`GET /api/admin/settlements?from={from}&to={to}&status={status}&format={format}`

권한: `ADMIN`

Query:

| 이름 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `from` | string | O | 시작 월, `yyyy-MM` |
| `to` | string | O | 종료 월, `yyyy-MM` |
| `status` | enum | X | `PENDING`, `CONFIRMED`, `PAID` |
| `format` | string | X | `csv` 지정 시 CSV 응답 |

JSON 응답:

```json
{
  "items": [
    {
      "id": 1,
      "creatorId": "creator-1",
      "yearMonth": "2025-03",
      "status": "PENDING",
      "snapshotAt": "2025-04-29T13:00:00Z",
      "totalSale": 260000,
      "totalRefund": 110000,
      "netSale": 150000,
      "totalFeeAmount": 30000,
      "payout": 120000,
      "lateArrivalSaleAmount": 0,
      "lateArrivalRefundAmount": 0,
      "lateArrivalFeeAmount": 0,
      "lateArrivalPayoutAmount": 0,
      "totalPayableAmount": 120000
    }
  ],
  "total": 1
}
```

### 7.4 정산 확정 처리

`PATCH /api/admin/settlements/{id}/confirm`

권한: `ADMIN`

규칙:

- `PENDING`만 `CONFIRMED`로 전이 가능
- 그 외 상태는 `INVALID_STATE_TRANSITION`

응답: `MonthlySettlementResponse`

### 7.5 정산 지급 처리

`PATCH /api/admin/settlements/{id}/pay`

권한: `ADMIN`

규칙:

- `CONFIRMED`만 `PAID`로 전이 가능
- 그 외 상태는 `INVALID_STATE_TRANSITION`

응답: `MonthlySettlementResponse`

### 7.6 정산 스냅샷 삭제

`DELETE /api/admin/settlements/{id}`

권한: `ADMIN`

규칙:

- `PENDING`만 삭제 가능
- 삭제 시 포함 원장의 `settlement_id` 연결을 해제한다.

응답:

- `204 No Content`

## 8. 샘플 데이터와 재시작 동작

기본 설정에서 `app.bootstrap-sample=true`다.

서버가 시작될 때마다 다음 순서로 기존 데이터를 초기화하고 샘플 데이터를 다시 적재한다.

1. 환불 원장
2. 판매 원장
3. 정산 스냅샷
4. 강의
5. 크리에이터
6. 수수료 정책
7. `sample-data.json` 재적재

운영 데이터 보존이 필요한 환경에서는 반드시 `app.bootstrap-sample=false`로 실행해야 한다.
