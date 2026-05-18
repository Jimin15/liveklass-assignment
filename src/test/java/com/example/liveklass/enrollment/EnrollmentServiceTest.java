package com.example.liveklass.enrollment;

import com.example.liveklass.global.BusinessException;
import com.example.liveklass.klass.Klass;
import com.example.liveklass.klass.KlassRepository;
import com.example.liveklass.user.User;
import com.example.liveklass.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnrollmentServiceTest {

    @Mock private ClassRegistrationRepository registrationRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private WaitlistEntryRepository waitlistEntryRepository;
    @Mock private KlassRepository klassRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private EnrollmentService enrollmentService;

    private Klass openKlass;
    private Klass fullKlass;

    @BeforeEach
    void setUp() {
        given(userRepository.findById(anyLong()))
                .willReturn(Optional.of(User.builder().name("테스트유저").email("test@test.com").build()));

        openKlass = Klass.builder()
                .creatorId(10L).title("Spring Boot 강의").description("설명")
                .price(BigDecimal.valueOf(50000)).capacity(10)
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(30))
                .build();
        ReflectionTestUtils.setField(openKlass, "id", 1L);

        fullKlass = Klass.builder()
                .creatorId(10L).title("인기 강의").description("설명")
                .price(BigDecimal.valueOf(50000)).capacity(1)
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(30))
                .build();
        ReflectionTestUtils.setField(fullKlass, "id", 2L);
        fullKlass.increaseReservedCount();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ClassRegistration pendingReg(Long klassId, Long userId) {
        ClassRegistration reg = ClassRegistration.builder()
                .klassId(klassId).userId(userId).status(RegistrationStatus.PENDING).build();
        ReflectionTestUtils.setField(reg, "id", 100L);
        return reg;
    }

    private ClassRegistration confirmedReg(Long klassId, Long userId) {
        ClassRegistration reg = ClassRegistration.builder()
                .klassId(klassId).userId(userId).status(RegistrationStatus.CONFIRMED).build();
        ReflectionTestUtils.setField(reg, "id", 100L);
        return reg;
    }

    private ClassRegistration waitlistedReg(Long klassId, Long userId) {
        ClassRegistration reg = ClassRegistration.builder()
                .klassId(klassId).userId(userId).status(RegistrationStatus.WAITLISTED).build();
        ReflectionTestUtils.setField(reg, "id", 100L);
        return reg;
    }

    // ── enroll ──────────────────────────────────────────────────────────────

    @Test
    void 수강신청_강의없으면_404() {
        given(klassRepository.findByIdWithLock(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> enrollmentService.enroll(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void 수강신청_OPEN아니면_400() {
        openKlass.updateStatus(com.example.liveklass.klass.ClassStatus.OPEN);
        openKlass.updateStatus(com.example.liveklass.klass.ClassStatus.CLOSED);
        given(klassRepository.findByIdWithLock(1L)).willReturn(Optional.of(openKlass));

        assertThatThrownBy(() -> enrollmentService.enroll(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 수강신청_중복이면_409() {
        openKlass.updateStatus(com.example.liveklass.klass.ClassStatus.OPEN);
        given(klassRepository.findByIdWithLock(1L)).willReturn(Optional.of(openKlass));
        given(registrationRepository.existsByKlassIdAndUserIdAndStatusIn(anyLong(), anyLong(), anyList()))
                .willReturn(true);

        assertThatThrownBy(() -> enrollmentService.enroll(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void 수강신청_자리있으면_PENDING() {
        openKlass.updateStatus(com.example.liveklass.klass.ClassStatus.OPEN);
        given(klassRepository.findByIdWithLock(1L)).willReturn(Optional.of(openKlass));
        given(registrationRepository.existsByKlassIdAndUserIdAndStatusIn(anyLong(), anyLong(), anyList()))
                .willReturn(false);

        ClassRegistration savedReg = pendingReg(1L, 1L);
        given(registrationRepository.save(any())).willReturn(savedReg);
        given(enrollmentRepository.save(any())).willReturn(
                Enrollment.builder().registrationId(100L).build());

        EnrollmentResponse response = enrollmentService.enroll(1L, 1L);

        assertThat(response.getStatus()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(response.getWaitlistPosition()).isNull();
    }

    @Test
    void 수강신청_자리없으면_WAITLISTED() {
        fullKlass.updateStatus(com.example.liveklass.klass.ClassStatus.OPEN);
        ReflectionTestUtils.setField(fullKlass, "waitlistNextSequence", 3);
        given(klassRepository.findByIdWithLock(2L)).willReturn(Optional.of(fullKlass));
        given(registrationRepository.existsByKlassIdAndUserIdAndStatusIn(anyLong(), anyLong(), anyList()))
                .willReturn(false);

        ClassRegistration savedReg = waitlistedReg(2L, 1L);
        given(registrationRepository.save(any())).willReturn(savedReg);
        given(waitlistEntryRepository.save(any())).willReturn(
                WaitlistEntry.builder().registrationId(100L).klassId(2L).userId(1L).sequence(3).build());
        given(waitlistEntryRepository.countByKlassIdAndStatusAndSequenceLessThan(2L, WaitlistStatus.WAITING, 3))
                .willReturn(2);

        EnrollmentResponse response = enrollmentService.enroll(1L, 2L);

        assertThat(response.getStatus()).isEqualTo(RegistrationStatus.WAITLISTED);
        assertThat(response.getWaitlistPosition()).isEqualTo(3);
    }

    @Test
    void 수강신청_첫번째_대기자는_position_1() {
        fullKlass.updateStatus(com.example.liveklass.klass.ClassStatus.OPEN);
        given(klassRepository.findByIdWithLock(2L)).willReturn(Optional.of(fullKlass));
        given(registrationRepository.existsByKlassIdAndUserIdAndStatusIn(anyLong(), anyLong(), anyList()))
                .willReturn(false);

        ClassRegistration savedReg = waitlistedReg(2L, 1L);
        given(registrationRepository.save(any())).willReturn(savedReg);
        given(waitlistEntryRepository.save(any())).willReturn(
                WaitlistEntry.builder().registrationId(100L).klassId(2L).userId(1L).sequence(1).build());
        given(waitlistEntryRepository.countByKlassIdAndStatusAndSequenceLessThan(2L, WaitlistStatus.WAITING, 1))
                .willReturn(0);

        EnrollmentResponse response = enrollmentService.enroll(1L, 2L);

        assertThat(response.getWaitlistPosition()).isEqualTo(1);
    }

    @Test
    void 수강신청_CANCELLED_후_재신청_가능() {
        openKlass.updateStatus(com.example.liveklass.klass.ClassStatus.OPEN);
        given(klassRepository.findByIdWithLock(1L)).willReturn(Optional.of(openKlass));
        given(registrationRepository.existsByKlassIdAndUserIdAndStatusIn(anyLong(), anyLong(), anyList()))
                .willReturn(false);

        ClassRegistration savedReg = pendingReg(1L, 1L);
        given(registrationRepository.save(any())).willReturn(savedReg);
        given(enrollmentRepository.save(any())).willReturn(
                Enrollment.builder().registrationId(100L).build());

        EnrollmentResponse response = enrollmentService.enroll(1L, 1L);

        assertThat(response.getStatus()).isEqualTo(RegistrationStatus.PENDING);
    }

    // ── confirm ─────────────────────────────────────────────────────────────

    @Test
    void 확정_신청없으면_404() {
        given(registrationRepository.findByKlassIdAndUserIdAndStatusIn(eq(1L), eq(1L), anyList()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> enrollmentService.confirm(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void 확정_PENDING아니면_400() {
        ClassRegistration reg = waitlistedReg(1L, 1L);
        given(registrationRepository.findByKlassIdAndUserIdAndStatusIn(eq(1L), eq(1L), anyList()))
                .willReturn(Optional.of(reg));

        assertThatThrownBy(() -> enrollmentService.confirm(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 확정_성공() {
        ClassRegistration reg = pendingReg(1L, 1L);
        Enrollment enrollment = Enrollment.builder().registrationId(100L).build();

        given(registrationRepository.findByKlassIdAndUserIdAndStatusIn(eq(1L), eq(1L), anyList()))
                .willReturn(Optional.of(reg));
        given(enrollmentRepository.findByRegistrationIdWithLock(100L)).willReturn(Optional.of(enrollment));
        given(klassRepository.findById(1L)).willReturn(Optional.of(openKlass));

        EnrollmentResponse response = enrollmentService.confirm(1L, 1L);

        assertThat(response.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
    }

    // ── cancel ──────────────────────────────────────────────────────────────

    @Test
    void 취소_활성신청_없으면_404() {
        given(registrationRepository.findByKlassIdAndUserIdAndStatusIn(eq(1L), eq(1L), anyList()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> enrollmentService.cancel(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void 취소_대기자_성공() {
        ClassRegistration reg = waitlistedReg(1L, 1L);
        WaitlistEntry entry = WaitlistEntry.builder()
                .registrationId(100L).klassId(1L).userId(1L).sequence(2).build();

        given(registrationRepository.findByKlassIdAndUserIdAndStatusIn(eq(1L), eq(1L), anyList()))
                .willReturn(Optional.of(reg));
        given(klassRepository.findByIdWithLock(1L)).willReturn(Optional.of(openKlass));
        given(waitlistEntryRepository.findByRegistrationId(100L)).willReturn(Optional.of(entry));

        EnrollmentResponse response = enrollmentService.cancel(1L, 1L);

        assertThat(response.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(entry.getStatus()).isEqualTo(WaitlistStatus.CANCELLED);
        assertThat(openKlass.getReservedCount()).isEqualTo(0); // no seat was held
        then(klassRepository).should().findByIdWithLock(1L);
    }

    @Test
    void 취소_PENDING_성공_좌석반납() {
        openKlass.updateStatus(com.example.liveklass.klass.ClassStatus.OPEN);
        openKlass.increaseReservedCount();

        ClassRegistration reg = pendingReg(1L, 1L);
        Enrollment enrollment = Enrollment.builder().registrationId(100L).build();

        given(registrationRepository.findByKlassIdAndUserIdAndStatusIn(eq(1L), eq(1L), anyList()))
                .willReturn(Optional.of(reg));
        given(klassRepository.findByIdWithLock(1L)).willReturn(Optional.of(openKlass));
        given(enrollmentRepository.findByRegistrationIdWithLock(100L)).willReturn(Optional.of(enrollment));
        given(waitlistEntryRepository.findFirstByKlassIdAndStatusOrderBySequenceAsc(eq(1L), eq(WaitlistStatus.WAITING)))
                .willReturn(Optional.empty());

        EnrollmentResponse response = enrollmentService.cancel(1L, 1L);

        assertThat(response.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(openKlass.getReservedCount()).isEqualTo(0);
    }

    @Test
    void 취소_CONFIRMED_7일이후_400() {
        ClassRegistration reg = confirmedReg(1L, 1L);
        Enrollment enrollment = Enrollment.builder()
                .registrationId(100L)
                .status(EnrollmentStatus.CONFIRMED)
                .confirmedAt(LocalDateTime.now().minusDays(8))
                .cancelDeadlineAt(LocalDateTime.now().minusDays(1))
                .build();

        given(registrationRepository.findByKlassIdAndUserIdAndStatusIn(eq(1L), eq(1L), anyList()))
                .willReturn(Optional.of(reg));
        given(enrollmentRepository.findByRegistrationId(100L)).willReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> enrollmentService.cancel(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 취소_CONFIRMED_7일이내_성공() {
        openKlass.updateStatus(com.example.liveklass.klass.ClassStatus.OPEN);
        openKlass.increaseReservedCount();

        ClassRegistration reg = confirmedReg(1L, 1L);
        Enrollment enrollment = Enrollment.builder()
                .registrationId(100L)
                .status(EnrollmentStatus.CONFIRMED)
                .confirmedAt(LocalDateTime.now().minusDays(3))
                .cancelDeadlineAt(LocalDateTime.now().plusDays(4))
                .build();

        given(registrationRepository.findByKlassIdAndUserIdAndStatusIn(eq(1L), eq(1L), anyList()))
                .willReturn(Optional.of(reg));
        given(enrollmentRepository.findByRegistrationId(100L)).willReturn(Optional.of(enrollment)); // fast-fail
        given(klassRepository.findByIdWithLock(1L)).willReturn(Optional.of(openKlass));
        given(enrollmentRepository.findByRegistrationIdWithLock(100L)).willReturn(Optional.of(enrollment)); // post-lock
        given(waitlistEntryRepository.findFirstByKlassIdAndStatusOrderBySequenceAsc(eq(1L), eq(WaitlistStatus.WAITING)))
                .willReturn(Optional.empty());

        EnrollmentResponse response = enrollmentService.cancel(1L, 1L);

        assertThat(response.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
    }

    @Test
    void 취소_PENDING_대기자_승격() {
        openKlass.updateStatus(com.example.liveklass.klass.ClassStatus.OPEN);
        openKlass.increaseReservedCount();

        ClassRegistration reg = pendingReg(1L, 1L);
        Enrollment enrollment = Enrollment.builder().registrationId(100L).build();

        ClassRegistration waitedReg = ClassRegistration.builder()
                .klassId(1L).userId(2L).status(RegistrationStatus.WAITLISTED).build();
        ReflectionTestUtils.setField(waitedReg, "id", 200L);

        WaitlistEntry waitedEntry = WaitlistEntry.builder()
                .registrationId(200L).klassId(1L).userId(2L).sequence(1).build();

        given(registrationRepository.findByKlassIdAndUserIdAndStatusIn(eq(1L), eq(1L), anyList()))
                .willReturn(Optional.of(reg));
        given(klassRepository.findByIdWithLock(1L)).willReturn(Optional.of(openKlass));
        given(enrollmentRepository.findByRegistrationIdWithLock(100L)).willReturn(Optional.of(enrollment));
        given(waitlistEntryRepository.findFirstByKlassIdAndStatusOrderBySequenceAsc(eq(1L), eq(WaitlistStatus.WAITING)))
                .willReturn(Optional.of(waitedEntry));
        given(registrationRepository.findById(200L)).willReturn(Optional.of(waitedReg));
        given(enrollmentRepository.save(any())).willReturn(Enrollment.builder().registrationId(200L).build());

        enrollmentService.cancel(1L, 1L);

        assertThat(waitedEntry.getStatus()).isEqualTo(WaitlistStatus.PROMOTED);
        assertThat(waitedReg.getStatus()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(openKlass.getReservedCount()).isEqualTo(1); // decreased then increased
        then(enrollmentRepository).should().save(argThat(e -> e.getRegistrationId().equals(200L)));
    }

    @Test
    void 취소_CONFIRMED_대기자_승격() {
        openKlass.updateStatus(com.example.liveklass.klass.ClassStatus.OPEN);
        openKlass.increaseReservedCount();

        ClassRegistration reg = confirmedReg(1L, 1L);
        Enrollment enrollment = Enrollment.builder()
                .registrationId(100L)
                .status(EnrollmentStatus.CONFIRMED)
                .confirmedAt(LocalDateTime.now().minusDays(1))
                .cancelDeadlineAt(LocalDateTime.now().plusDays(6))
                .build();

        ClassRegistration waitedReg = ClassRegistration.builder()
                .klassId(1L).userId(2L).status(RegistrationStatus.WAITLISTED).build();
        ReflectionTestUtils.setField(waitedReg, "id", 200L);

        WaitlistEntry waitedEntry = WaitlistEntry.builder()
                .registrationId(200L).klassId(1L).userId(2L).sequence(1).build();

        given(registrationRepository.findByKlassIdAndUserIdAndStatusIn(eq(1L), eq(1L), anyList()))
                .willReturn(Optional.of(reg));
        given(enrollmentRepository.findByRegistrationId(100L)).willReturn(Optional.of(enrollment)); // fast-fail
        given(klassRepository.findByIdWithLock(1L)).willReturn(Optional.of(openKlass));
        given(enrollmentRepository.findByRegistrationIdWithLock(100L)).willReturn(Optional.of(enrollment)); // post-lock
        given(waitlistEntryRepository.findFirstByKlassIdAndStatusOrderBySequenceAsc(eq(1L), eq(WaitlistStatus.WAITING)))
                .willReturn(Optional.of(waitedEntry));
        given(registrationRepository.findById(200L)).willReturn(Optional.of(waitedReg));
        given(enrollmentRepository.save(any())).willReturn(Enrollment.builder().registrationId(200L).build());

        enrollmentService.cancel(1L, 1L);

        assertThat(waitedEntry.getStatus()).isEqualTo(WaitlistStatus.PROMOTED);
        assertThat(waitedReg.getStatus()).isEqualTo(RegistrationStatus.PENDING);
    }

    // ── getMyEnrollments ────────────────────────────────────────────────────

    @Test
    void 내_수강목록_페이지네이션() {
        ClassRegistration reg = pendingReg(1L, 1L);
        Enrollment enrollment = Enrollment.builder().registrationId(100L).build();

        given(registrationRepository.findByUserId(eq(1L), any()))
                .willReturn(new PageImpl<>(List.of(reg)));
        given(klassRepository.findById(1L)).willReturn(Optional.of(openKlass));
        given(enrollmentRepository.findByRegistrationId(100L)).willReturn(Optional.of(enrollment));

        Page<EnrollmentResponse> result = enrollmentService.getMyEnrollments(1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(RegistrationStatus.PENDING);
    }

    // ── getEnrollmentsByKlass ────────────────────────────────────────────────

    @Test
    void 강의별_수강목록_권한없으면_403() {
        given(klassRepository.findById(1L)).willReturn(Optional.of(openKlass));

        assertThatThrownBy(() -> enrollmentService.getEnrollmentsByKlass(999L, 1L, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void 강의별_수강목록_크리에이터_성공() {
        ClassRegistration reg = pendingReg(1L, 2L);
        Enrollment enrollment = Enrollment.builder().registrationId(100L).build();

        given(klassRepository.findById(1L)).willReturn(Optional.of(openKlass));
        given(registrationRepository.findByKlassId(eq(1L), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(reg)));
        given(enrollmentRepository.findByRegistrationId(100L)).willReturn(Optional.of(enrollment));

        Page<CreatorEnrollmentResponse> result = enrollmentService.getEnrollmentsByKlass(10L, 1L, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(RegistrationStatus.PENDING);
    }
}
