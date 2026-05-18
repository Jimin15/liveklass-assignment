package com.example.liveklass.enrollment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassRegistrationRepository extends JpaRepository<ClassRegistration, Long> {

    boolean existsByKlassIdAndUserIdAndStatusIn(Long klassId, Long userId, List<RegistrationStatus> statuses);

    Optional<ClassRegistration> findByKlassIdAndUserIdAndStatusIn(Long klassId, Long userId, List<RegistrationStatus> statuses);

    Page<ClassRegistration> findByUserId(Long userId, Pageable pageable);

    List<ClassRegistration> findByKlassId(Long klassId);

    Page<ClassRegistration> findByKlassId(Long klassId, Pageable pageable);
}
