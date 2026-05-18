package com.example.liveklass.enrollment;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "waitlist_entry",
        indexes = {
                @Index(name = "idx_waitlist_registration", columnList = "registration_id", unique = true),
                @Index(name = "idx_waitlist_klass_status", columnList = "klass_id, status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long registrationId;

    @Column(nullable = false)
    private Long klassId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WaitlistStatus status;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime promotedAt;

    private LocalDateTime cancelledAt;

    @Builder
    public WaitlistEntry(Long registrationId, Long klassId, Long userId) {
        this.registrationId = registrationId;
        this.klassId = klassId;
        this.userId = userId;
        this.status = WaitlistStatus.WAITING;
    }

    public void promote() {
        this.status = WaitlistStatus.PROMOTED;
        this.promotedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = WaitlistStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }
}
