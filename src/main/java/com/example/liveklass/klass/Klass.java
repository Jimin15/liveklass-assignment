package com.example.liveklass.klass;

import com.example.liveklass.global.BusinessException;
import com.example.liveklass.global.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "klass",
        indexes = {
                @Index(name = "idx_klass_status", columnList = "status"),
                @Index(name = "idx_klass_creator", columnList = "creator_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Klass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long creatorId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int reservedCount = 0;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClassStatus status = ClassStatus.DRAFT;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Klass(Long creatorId, String title, String description, BigDecimal price,
                 int capacity, LocalDate startDate, LocalDate endDate) {
        this.creatorId = creatorId;
        this.title = title;
        this.description = description;
        this.price = price;
        this.capacity = capacity;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = ClassStatus.DRAFT;
        this.reservedCount = 0;
    }

    public void updateStatus(ClassStatus newStatus) {
        if (this.status == ClassStatus.CLOSED) {
            throw new BusinessException(ErrorCode.KLASS_ALREADY_CLOSED);
        }
        if (this.status == ClassStatus.OPEN && newStatus == ClassStatus.DRAFT) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = newStatus;
    }

    public void increaseReservedCount() {
        this.reservedCount++;
    }

    public void decreaseReservedCount() {
        if (this.reservedCount <= 0) {
            throw new BusinessException(ErrorCode.RESERVED_COUNT_UNDERFLOW);
        }
        this.reservedCount--;
    }

    public boolean isFull() {
        return this.reservedCount >= this.capacity;
    }

    public int getAvailableSeats() {
        return this.capacity - this.reservedCount;
    }
}
