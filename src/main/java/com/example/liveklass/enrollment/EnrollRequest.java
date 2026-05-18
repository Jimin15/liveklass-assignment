package com.example.liveklass.enrollment;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EnrollRequest {

    @NotNull(message = "강의 ID는 필수입니다.")
    private Long classId;
}
