package com.example.liveklass.enrollment;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class EnrollmentResponse {

    private Long registrationId;
    private Long classId;
    private String classTitle;
    private Long userId;
    private RegistrationStatus status;
    private Integer waitlistPosition;
    private LocalDateTime reservedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime cancelDeadlineAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static EnrollmentResponse from(ClassRegistration registration, Enrollment enrollment,
                                           String classTitle, Integer waitlistPosition) {
        return EnrollmentResponse.builder()
                .registrationId(registration.getId())
                .classId(registration.getKlassId())
                .classTitle(classTitle)
                .userId(registration.getUserId())
                .status(registration.getStatus())
                .waitlistPosition(waitlistPosition)
                .reservedAt(enrollment != null ? enrollment.getReservedAt() : null)
                .confirmedAt(enrollment != null ? enrollment.getConfirmedAt() : null)
                .cancelDeadlineAt(enrollment != null ? enrollment.getCancelDeadlineAt() : null)
                .createdAt(registration.getCreatedAt())
                .updatedAt(registration.getUpdatedAt())
                .build();
    }
}
