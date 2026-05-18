package com.example.liveklass.klass;

import com.example.liveklass.enrollment.CreatorEnrollmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Class")
public interface KlassApi {

    @Operation(summary = "강의 생성", description = "새 강의를 DRAFT 상태로 생성합니다. 이후 PATCH로 OPEN 전환해야 수강 신청이 가능합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "강의 생성 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 오류")
    })
    ResponseEntity<KlassResponse> createKlass(
            @Parameter(description = "크리에이터 유저 ID", required = true) @RequestHeader("X-User-Id") Long creatorId,
            @Valid @RequestBody KlassCreateRequest request);

    @Operation(summary = "강의 목록 조회", description = "전체 강의 목록을 조회합니다. `status` 쿼리 파라미터로 DRAFT / OPEN / CLOSED 필터링 가능합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ResponseEntity<List<KlassResponse>> getKlasses(
            @Parameter(description = "강의 상태 필터 (DRAFT / OPEN / CLOSED)") @RequestParam(required = false) ClassStatus status);

    @Operation(summary = "강의 단건 조회", description = "강의 ID로 단건 조회합니다. 현재 신청 인원(reservedCount)을 포함합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "강의 없음")
    })
    ResponseEntity<KlassResponse> getKlass(
            @Parameter(description = "강의 ID", required = true) @PathVariable Long id);

    @Operation(summary = "강의 상태 변경", description = "강의 상태를 변경합니다. DRAFT → OPEN → CLOSED 순으로만 전환 가능하며 역방향은 불가합니다. 크리에이터 본인만 변경할 수 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "상태 변경 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 상태 전환 (역방향 등)"),
            @ApiResponse(responseCode = "403", description = "크리에이터 본인이 아님"),
            @ApiResponse(responseCode = "404", description = "강의 없음")
    })
    ResponseEntity<KlassResponse> updateStatus(
            @Parameter(description = "크리에이터 유저 ID", required = true) @RequestHeader("X-User-Id") Long creatorId,
            @Parameter(description = "강의 ID", required = true) @PathVariable Long id,
            @Valid @RequestBody KlassStatusUpdateRequest request);

    @Operation(summary = "강의별 수강생 목록 조회", description = "해당 강의의 수강 신청 목록을 페이지 단위로 조회합니다. 강의를 개설한 크리에이터만 접근 가능합니다.\n\n`sort` 파라미터는 비워두거나 `id,asc` 형식으로 입력하세요.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "크리에이터 본인이 아님"),
            @ApiResponse(responseCode = "404", description = "강의 없음")
    })
    ResponseEntity<Page<CreatorEnrollmentResponse>> getEnrollmentsByKlass(
            @Parameter(description = "크리에이터 유저 ID", required = true) @RequestHeader("X-User-Id") Long creatorId,
            @Parameter(description = "강의 ID", required = true) @PathVariable Long id,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable);
}
