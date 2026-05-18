package com.example.liveklass.enrollment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollmentController implements EnrollmentApi {

    private final EnrollmentService enrollmentService;

    @PostMapping
    public ResponseEntity<EnrollmentResponse> enroll(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody EnrollRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(enrollmentService.enroll(userId, request.getClassId()));
    }

    @PostMapping("/confirm")
    public ResponseEntity<EnrollmentResponse> confirm(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody EnrollRequest request) {
        return ResponseEntity.ok(enrollmentService.confirm(userId, request.getClassId()));
    }

    @PostMapping("/cancel")
    public ResponseEntity<EnrollmentResponse> cancel(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody EnrollRequest request) {
        return ResponseEntity.ok(enrollmentService.cancel(userId, request.getClassId()));
    }

    @GetMapping("/me")
    public ResponseEntity<Page<EnrollmentResponse>> getMyEnrollments(
            @RequestHeader("X-User-Id") Long userId,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(enrollmentService.getMyEnrollments(userId, pageable));
    }

}
