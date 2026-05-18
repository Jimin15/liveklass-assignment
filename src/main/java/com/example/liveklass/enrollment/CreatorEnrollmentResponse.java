package com.example.liveklass.enrollment;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CreatorEnrollmentResponse {

    private Long enrollmentId;
    private Long userId;
    private RegistrationStatus status;
    private Integer waitlistPosition;
    private LocalDateTime confirmedAt;
    private LocalDateTime cancelDeadlineAt;

    public static CreatorEnrollmentResponse from(ClassRegistration registration, Enrollment enrollment,
                                                   Integer waitlistPosition) {
        return CreatorEnrollmentResponse.builder()
                .enrollmentId(registration.getId())
                .userId(registration.getUserId())
                .status(registration.getStatus())
                .waitlistPosition(waitlistPosition)
                .confirmedAt(enrollment != null ? enrollment.getConfirmedAt() : null)
                .cancelDeadlineAt(enrollment != null ? enrollment.getCancelDeadlineAt() : null)
                .build();
    }
}
