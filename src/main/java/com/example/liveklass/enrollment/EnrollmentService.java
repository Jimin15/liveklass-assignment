package com.example.liveklass.enrollment;

import com.example.liveklass.global.BusinessException;
import com.example.liveklass.global.ErrorCode;
import com.example.liveklass.klass.ClassStatus;
import com.example.liveklass.klass.Klass;
import com.example.liveklass.klass.KlassRepository;
import com.example.liveklass.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final ClassRegistrationRepository registrationRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final KlassRepository klassRepository;
    private final UserRepository userRepository;

    @Transactional
    public EnrollmentResponse enroll(Long userId, Long klassId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Klass klass = klassRepository.findByIdWithLock(klassId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KLASS_NOT_FOUND));

        if (klass.getStatus() != ClassStatus.OPEN) {
            throw new BusinessException(ErrorCode.KLASS_NOT_OPEN);
        }

        boolean alreadyActive = registrationRepository.existsByKlassIdAndUserIdAndStatusIn(
                klassId, userId,
                List.of(RegistrationStatus.PENDING, RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED)
        );
        if (alreadyActive) {
            throw new BusinessException(ErrorCode.ENROLLMENT_DUPLICATE);
        }

        if (!klass.isFull()) {
            klass.increaseReservedCount();
            ClassRegistration registration = registrationRepository.save(
                    ClassRegistration.builder()
                            .klassId(klassId).userId(userId).status(RegistrationStatus.PENDING).build()
            );
            enrollmentRepository.save(
                    Enrollment.builder().registrationId(registration.getId()).build()
            );
            return EnrollmentResponse.from(registration, null, klass.getTitle(), null);
        } else {
            ClassRegistration registration = registrationRepository.save(
                    ClassRegistration.builder()
                            .klassId(klassId).userId(userId).status(RegistrationStatus.WAITLISTED).build()
            );
            WaitlistEntry entry = waitlistEntryRepository.save(
                    WaitlistEntry.builder()
                            .registrationId(registration.getId())
                            .klassId(klassId).userId(userId).build()
            );
            int position = waitlistEntryRepository.countByKlassIdAndStatusAndIdLessThan(
                    klassId, WaitlistStatus.WAITING, entry.getId()) + 1;
            return EnrollmentResponse.from(registration, null, klass.getTitle(), position);
        }
    }

    @Transactional
    public EnrollmentResponse confirm(Long userId, Long klassId) {
        ClassRegistration registration = registrationRepository.findByKlassIdAndUserIdAndStatusIn(
                klassId, userId,
                List.of(RegistrationStatus.PENDING, RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED)
        ).orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));

        if (registration.getStatus() != RegistrationStatus.PENDING) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_PENDING);
        }

        Enrollment enrollment = enrollmentRepository.findByRegistrationIdWithLock(registration.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));

        if (enrollment.getStatus() != EnrollmentStatus.PENDING) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_PENDING);
        }

        enrollment.confirm();
        registration.confirm();

        String title = klassRepository.findById(klassId)
                .map(Klass::getTitle).orElse("(삭제된 강의)");
        return EnrollmentResponse.from(registration, enrollment, title, null);
    }

    @Transactional
    public EnrollmentResponse cancel(Long userId, Long klassId) {
        ClassRegistration registration = registrationRepository.findByKlassIdAndUserIdAndStatusIn(
                klassId, userId,
                List.of(RegistrationStatus.PENDING, RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED)
        ).orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));

        RegistrationStatus status = registration.getStatus();
        Enrollment enrollment = null;

        // Fast-fail deadline check before acquiring klass lock (avoids unnecessary lock contention)
        if (status == RegistrationStatus.CONFIRMED) {
            Enrollment preCheck = enrollmentRepository.findByRegistrationId(registration.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
            checkCancelDeadline(preCheck);
        }

        // All cancellations require klass lock (WAITLISTED cancel also prevents race with promotion)
        Klass klass = klassRepository.findByIdWithLock(klassId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KLASS_NOT_FOUND));

        if (status == RegistrationStatus.PENDING || status == RegistrationStatus.CONFIRMED) {
            // Re-fetch with lock to get fresh DB state — prevents double-cancel corrupting reservedCount
            enrollment = enrollmentRepository.findByRegistrationIdWithLock(registration.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
            if (enrollment.getStatus() == EnrollmentStatus.CANCELLED) {
                throw new BusinessException(ErrorCode.INVALID_CANCEL_STATUS);
            }
            if (status == RegistrationStatus.CONFIRMED) {
                checkCancelDeadline(enrollment); // final validation with fresh data
            }
            enrollment.cancel();
            klass.decreaseReservedCount();
            promoteFirstWaiting(klass);
        } else {
            WaitlistEntry entry = waitlistEntryRepository.findByRegistrationId(registration.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
            entry.cancel();
        }

        registration.cancel();

        return EnrollmentResponse.from(registration, enrollment, klass.getTitle(), null);
    }

    private void checkCancelDeadline(Enrollment enrollment) {
        if (enrollment.getCancelDeadlineAt() != null
                && enrollment.getCancelDeadlineAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.CANCEL_PERIOD_EXPIRED);
        }
    }

    private void promoteFirstWaiting(Klass klass) {
        waitlistEntryRepository.findFirstByKlassIdAndStatusOrderByIdAsc(
                klass.getId(), WaitlistStatus.WAITING
        ).ifPresent(entry -> {
            entry.promote();
            ClassRegistration waitedRegistration = registrationRepository.findById(entry.getRegistrationId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
            waitedRegistration.promoteFromWaitlist();
            enrollmentRepository.save(
                    Enrollment.builder().registrationId(entry.getRegistrationId()).build()
            );
            klass.increaseReservedCount();
        });
    }

    @Transactional(readOnly = true)
    public Page<EnrollmentResponse> getMyEnrollments(Long userId, Pageable pageable) {
        return registrationRepository.findByUserId(userId, pageable).map(reg -> {
            String title = klassRepository.findById(reg.getKlassId())
                    .map(Klass::getTitle).orElse("(삭제된 강의)");
            Enrollment enrollment = null;
            if (reg.getStatus() == RegistrationStatus.PENDING
                    || reg.getStatus() == RegistrationStatus.CONFIRMED) {
                enrollment = enrollmentRepository.findByRegistrationId(reg.getId()).orElse(null);
            }
            Integer position = null;
            if (reg.getStatus() == RegistrationStatus.WAITLISTED) {
                position = waitlistEntryRepository.findByRegistrationId(reg.getId())
                        .map(e -> waitlistEntryRepository.countByKlassIdAndStatusAndIdLessThan(
                                e.getKlassId(), WaitlistStatus.WAITING, e.getId()) + 1)
                        .orElse(null);
            }
            return EnrollmentResponse.from(reg, enrollment, title, position);
        });
    }

    @Transactional(readOnly = true)
    public Page<CreatorEnrollmentResponse> getEnrollmentsByKlass(Long creatorId, Long klassId, Pageable pageable) {
        Klass klass = klassRepository.findById(klassId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KLASS_NOT_FOUND));
        if (!klass.getCreatorId().equals(creatorId)) {
            throw new BusinessException(ErrorCode.ENROLLMENT_ACCESS_DENIED);
        }
        return registrationRepository.findByKlassId(klassId, pageable)
                .map(reg -> {
                    Enrollment enrollment = null;
                    if (reg.getStatus() == RegistrationStatus.PENDING
                            || reg.getStatus() == RegistrationStatus.CONFIRMED) {
                        enrollment = enrollmentRepository.findByRegistrationId(reg.getId()).orElse(null);
                    }
                    Integer position = null;
                    if (reg.getStatus() == RegistrationStatus.WAITLISTED) {
                        position = waitlistEntryRepository.findByRegistrationId(reg.getId())
                                .map(e -> waitlistEntryRepository.countByKlassIdAndStatusAndIdLessThan(
                                        e.getKlassId(), WaitlistStatus.WAITING, e.getId()) + 1)
                                .orElse(null);
                    }
                    return CreatorEnrollmentResponse.from(reg, enrollment, position);
                });
    }
}
