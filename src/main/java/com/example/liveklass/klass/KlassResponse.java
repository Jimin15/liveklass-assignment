package com.example.liveklass.klass;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class KlassResponse {

    private Long id;
    private Long creatorId;
    private String title;
    private String description;
    private BigDecimal price;
    private int capacity;
    private int reservedCount;
    private int availableSeats;
    private LocalDate startDate;
    private LocalDate endDate;
    private ClassStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static KlassResponse from(Klass klass) {
        return KlassResponse.builder()
                .id(klass.getId())
                .creatorId(klass.getCreatorId())
                .title(klass.getTitle())
                .description(klass.getDescription())
                .price(klass.getPrice())
                .capacity(klass.getCapacity())
                .reservedCount(klass.getReservedCount())
                .availableSeats(klass.getAvailableSeats())
                .startDate(klass.getStartDate())
                .endDate(klass.getEndDate())
                .status(klass.getStatus())
                .createdAt(klass.getCreatedAt())
                .updatedAt(klass.getUpdatedAt())
                .build();
    }
}
