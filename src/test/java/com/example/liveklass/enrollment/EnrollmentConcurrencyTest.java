package com.example.liveklass.enrollment;

import com.example.liveklass.klass.Klass;
import com.example.liveklass.klass.KlassRepository;
import com.example.liveklass.user.User;
import com.example.liveklass.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EnrollmentConcurrencyTest {

    @Autowired private EnrollmentService enrollmentService;
    @Autowired private KlassRepository klassRepository;
    @Autowired private ClassRegistrationRepository registrationRepository;
    @Autowired private WaitlistEntryRepository waitlistEntryRepository;
    @Autowired private UserRepository userRepository;

    private Long klassId;
    private List<Long> testUserIds;

    @BeforeEach
    void setUp() {
        waitlistEntryRepository.deleteAll();
        registrationRepository.deleteAll();
        klassRepository.deleteAll();
        userRepository.deleteAll();

        testUserIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            User user = userRepository.save(
                    User.builder().name("user" + i).email("user" + i + "@test.com").build());
            testUserIds.add(user.getId());
        }

        Klass klass = Klass.builder()
                .creatorId(testUserIds.get(0))
                .title("동시성 테스트 강의").description("설명")
                .price(BigDecimal.valueOf(10000)).capacity(1)
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(30))
                .build();
        klass.updateStatus(com.example.liveklass.klass.ClassStatus.OPEN);
        klassId = klassRepository.save(klass).getId();
    }

    @Test
    void 동시수강신청_정원1명_초과없이_대기열처리() throws InterruptedException {
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final long userId = testUserIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    enrollmentService.enroll(userId, klassId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        // All 5 requests succeed: 1 PENDING + 4 WAITLISTED (no capacity overflow)
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(errorCount.get()).isEqualTo(0);

        List<ClassRegistration> registrations = registrationRepository.findByKlassId(klassId);
        long pendingCount = registrations.stream()
                .filter(r -> r.getStatus() == RegistrationStatus.PENDING).count();
        long waitlistedCount = registrations.stream()
                .filter(r -> r.getStatus() == RegistrationStatus.WAITLISTED).count();

        assertThat(pendingCount).isEqualTo(1);
        assertThat(waitlistedCount).isEqualTo(threadCount - 1);

        // Waitlist sequences must be unique and contiguous starting from 1
        List<Integer> sequences = waitlistEntryRepository.findAll().stream()
                .filter(e -> e.getStatus() == WaitlistStatus.WAITING)
                .map(WaitlistEntry::getSequence)
                .sorted()
                .toList();
        for (int i = 0; i < sequences.size(); i++) {
            assertThat(sequences.get(i)).isEqualTo(i + 1);
        }
    }

    @Test
    void 동시수강신청_reservedCount_정원초과_없음() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final long userId = testUserIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    enrollmentService.enroll(userId, klassId);
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        Klass result = klassRepository.findById(klassId).orElseThrow();
        // reservedCount must never exceed capacity (1)
        assertThat(result.getReservedCount()).isLessThanOrEqualTo(result.getCapacity());
    }
}
