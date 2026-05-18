# 환경 설정

## 기술 스택

| 항목 | 선택 | 이유 |
|------|------|------|
| Framework | Spring Boot 3.5 (Java 21) | 기존 프로젝트 세팅 |
| ORM | Spring Data JPA | build.gradle에 이미 포함 |
| DB (운영) | MySQL 8 | EOL 종료된 5.7 대신 현재 표준 버전, utf8mb4 기본 내장 |
| DB (테스트) | H2 in-memory | Docker 없이 테스트 가능, 빠른 실행 속도 |
| 인증 | X-User-Id 헤더 | 과제 허용 간략화 방식 |

---

## 실행 방법

```bash
# MySQL 컨테이너 실행
docker-compose up -d

# 앱 실행 (localhost:8080)
./gradlew bootRun

# 테스트 실행 (H2 자동 사용, Docker 불필요)
./gradlew test
```

---

## 환경별 설정

### 운영 (application.properties)

```
DB: MySQL 8 (Docker)
URL: jdbc:mysql://localhost:3306/liveklass
ddl-auto: update
```

### 테스트 (application-test.properties)

```
DB: H2 in-memory
MODE=MySQL 로 MySQL 문법 최대한 호환
ddl-auto: create-drop (테스트마다 스키마 초기화)
```

> H2는 MySQL ENUM 타입을 지원하지 않으므로 Entity에서 반드시 `@Enumerated(EnumType.STRING)` 사용

---

## docker-compose 구성

| 항목 | 값 |
|------|-----|
| 이미지 | mysql:8 |
| 컨테이너명 | liveklass-mysql |
| 포트 | 3306:3306 |
| DB명 | liveklass |
| 계정 | liveklass / liveklass1234 |
| 데이터 영속성 | Docker volume (mysql_data) |
| healthcheck | mysqladmin ping (10초 간격, 최대 5회 재시도) |

---

## build.gradle 변경 사항

```groovy
// 변경 전
runtimeOnly 'com.h2database:h2'

// 변경 후
runtimeOnly 'com.mysql:mysql-connector-j'   // 운영용 MySQL 커넥터
testRuntimeOnly 'com.h2database:h2'         // 테스트 전용 H2
```
