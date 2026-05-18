package com.example.liveklass.klass;

import com.example.liveklass.enrollment.CreatorEnrollmentResponse;
import com.example.liveklass.enrollment.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/classes")
@RequiredArgsConstructor
public class KlassController implements KlassApi {

    private final KlassService klassService;
    private final EnrollmentService enrollmentService;

    @PostMapping
    public ResponseEntity<KlassResponse> createKlass(
            @RequestHeader("X-User-Id") Long creatorId,
            @Valid @RequestBody KlassCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(klassService.createKlass(creatorId, request));
    }

    @GetMapping
    public ResponseEntity<List<KlassResponse>> getKlasses(
            @RequestParam(required = false) ClassStatus status) {
        return ResponseEntity.ok(klassService.getKlasses(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<KlassResponse> getKlass(@PathVariable Long id) {
        return ResponseEntity.ok(klassService.getKlass(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<KlassResponse> updateStatus(
            @RequestHeader("X-User-Id") Long creatorId,
            @PathVariable Long id,
            @Valid @RequestBody KlassStatusUpdateRequest request) {
        return ResponseEntity.ok(klassService.updateStatus(creatorId, id, request));
    }

    @GetMapping("/{id}/enrollments")
    public ResponseEntity<Page<CreatorEnrollmentResponse>> getEnrollmentsByKlass(
            @RequestHeader("X-User-Id") Long creatorId,
            @PathVariable Long id,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(enrollmentService.getEnrollmentsByKlass(creatorId, id, pageable));
    }
}
