package com.example.liveklass.global;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
        info = @Info(
                title = "LiveKlass API",
                version = "v1",
                description = "크리에이터가 강의를 개설하고 수강생이 신청·확정·취소하는 수강 신청 시스템입니다.\n\n" +
                        "**인증 방식:** 모든 API는 `X-User-Id` 헤더로 사용자 ID를 전달합니다. " +
                        "유저가 없으면 `POST /api/users`로 먼저 생성하세요.\n\n" +
                        "**에러 응답 형식:** `{ \"error\": \"에러 메시지\" }`"
        ),
        tags = {
                @Tag(name = "User"),
                @Tag(name = "Class"),
                @Tag(name = "Enrollment")
        }
)
@Configuration
public class OpenApiConfig {
}
