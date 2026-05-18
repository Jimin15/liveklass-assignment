package com.example.liveklass.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "User")
public interface UserApi {

    @Operation(summary = "유저 생성", description = "유저를 생성하고 ID를 반환합니다. 반환된 ID를 `X-User-Id` 헤더에 사용하세요.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "유저 생성 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 오류 (이름/이메일 누락)"),
            @ApiResponse(responseCode = "409", description = "이미 사용 중인 이메일")
    })
    ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request);
}
