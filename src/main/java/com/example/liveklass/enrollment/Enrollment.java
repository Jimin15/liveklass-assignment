package com.example.liveklass.enrollment;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "enrollment",
        indexes = {
                @Index(name = "idx_enrollment_registration", columnList = "registration_id", unique = true)
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long registrationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrollmentStatus status;

    @Column(nullable = false)
    private LocalDateTime reservedAt;

    private LocalDateTime confirmedAt;

    private LocalDateTime cancelledAt;

    private LocalDateTime cancelDeadlineAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Enrollment(Long registrationId, EnrollmentStatus status,
                      LocalDateTime confirmedAt, LocalDateTime cancelDeadlineAt) {
        this.registrationId = registrationId;
        this.status = (status != null) ? status : EnrollmentStatus.PENDING;
        this.confirmedAt = confirmedAt;
        this.cancelDeadlineAt = cancelDeadlineAt;
        this.reservedAt = LocalDateTime.now();
    }

    public void confirm() {
        LocalDateTime now = LocalDateTime.now();
        this.status = EnrollmentStatus.CONFIRMED;
        this.confirmedAt = now;
        this.cancelDeadlineAt = now.plusDays(7);
    }

    public void cancel() {
        this.status = EnrollmentStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }
}
