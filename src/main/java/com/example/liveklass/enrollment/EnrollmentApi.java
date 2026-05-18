package com.example.liveklass.enrollment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Enrollment")
public interface EnrollmentApi {

    @Operation(summary = "수강 신청", description = "강의에 수강 신청합니다.\n\n" +
            "- 자리가 있으면 **PENDING** (결제 대기)\n" +
            "- 정원 초과면 **WAITLISTED** (대기열 등록, `waitlistPosition` 반환)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "신청 성공 (PENDING 또는 WAITLISTED)"),
            @ApiResponse(responseCode = "400", description = "OPEN 상태가 아닌 강의"),
            @ApiResponse(responseCode = "404", description = "유저 또는 강의 없음"),
            @ApiResponse(responseCode = "409", description = "이미 신청 중인 강의 (PENDING / CONFIRMED / WAITLISTED 중복)"),
            @ApiResponse(responseCode = "503", description = "동시 요청 과부하 — 잠시 후 재시도")
    })
    ResponseEntity<EnrollmentResponse> enroll(
            @Parameter(description = "수강생 유저 ID", required = true) @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody EnrollRequest request);

    @Operation(summary = "수강 확정", description = "PENDING 상태의 신청을 CONFIRMED로 확정합니다. 확정 시점으로부터 **7일간** 취소 가능하며, 이후에는 취소 불가입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "확정 성공"),
            @ApiResponse(responseCode = "400", description = "PENDING 상태가 아님"),
            @ApiResponse(responseCode = "404", description = "신청 내역 없음")
    })
    ResponseEntity<EnrollmentResponse> confirm(
            @Parameter(description = "수강생 유저 ID", required = true) @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody EnrollRequest request);

    @Operation(summary = "수강 취소", description = "수강 신청을 취소합니다.\n\n" +
            "- **PENDING**: 기간 제한 없이 취소 가능\n" +
            "- **CONFIRMED**: 확정 후 7일 이내만 취소 가능\n" +
            "- **WAITLISTED**: 대기 포기, 기간 제한 없음\n\n" +
            "PENDING/CONFIRMED 취소 시 대기자가 자동으로 PENDING으로 승격됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 성공"),
            @ApiResponse(responseCode = "400", description = "취소 불가 — CONFIRMED 7일 초과 또는 이미 취소됨"),
            @ApiResponse(responseCode = "404", description = "활성 신청 내역 없음"),
            @ApiResponse(responseCode = "503", description = "동시 요청 과부하 — 잠시 후 재시도")
    })
    ResponseEntity<EnrollmentResponse> cancel(
            @Parameter(description = "수강생 유저 ID", required = true) @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody EnrollRequest request);

    @Operation(summary = "내 수강 목록 조회", description = "로그인한 사용자의 수강 신청 내역을 최신순으로 페이지 단위 조회합니다.\n\n`sort` 파라미터는 비워두거나 `createdAt,desc` 형식으로 입력하세요.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ResponseEntity<Page<EnrollmentResponse>> getMyEnrollments(
            @Parameter(description = "수강생 유저 ID", required = true) @RequestHeader("X-User-Id") Long userId,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable);
}
