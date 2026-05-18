package com.example.liveklass.klass;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KlassStatusUpdateRequest {

    @NotNull(message = "상태는 필수입니다.")
    private ClassStatus status;
}
