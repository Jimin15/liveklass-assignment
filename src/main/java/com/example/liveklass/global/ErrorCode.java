package com.example.liveklass.global;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),

    // Klass
    KLASS_NOT_FOUND(HttpStatus.NOT_FOUND, "강의를 찾을 수 없습니다."),
    KLASS_ACCESS_DENIED(HttpStatus.FORBIDDEN, "강의 상태를 변경할 권한이 없습니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "시작일은 종료일보다 이전이어야 합니다."),
    KLASS_NOT_OPEN(HttpStatus.BAD_REQUEST, "수강 신청이 불가능한 강의입니다."),
    KLASS_ALREADY_CLOSED(HttpStatus.BAD_REQUEST, "마감된 강의는 상태를 변경할 수 없습니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "모집 중인 강의는 초안으로 변경할 수 없습니다."),

    // Enrollment
    ENROLLMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "수강 신청 내역을 찾을 수 없습니다."),
    ENROLLMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    ENROLLMENT_DUPLICATE(HttpStatus.CONFLICT, "이미 수강 신청된 강의입니다."),
    ENROLLMENT_NOT_PENDING(HttpStatus.BAD_REQUEST, "PENDING 상태에서만 확정할 수 있습니다."),
    CANCEL_PERIOD_EXPIRED(HttpStatus.BAD_REQUEST, "확정 후 7일이 지나 취소가 불가능합니다."),
    INVALID_CANCEL_STATUS(HttpStatus.BAD_REQUEST, "취소할 수 없는 상태입니다."),

    // System
    RESERVED_COUNT_UNDERFLOW(HttpStatus.INTERNAL_SERVER_ERROR, "예약 인원이 0 이하가 될 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
