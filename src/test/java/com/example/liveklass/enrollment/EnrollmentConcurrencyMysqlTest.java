package com.example.liveklass.enrollment;

import com.example.liveklass.klass.ClassStatus;
import com.example.liveklass.klass.Klass;
import com.example.liveklass.klass.KlassRepository;
import com.example.liveklass.user.User;
import com.example.liveklass.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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

@Tag("concurrency")
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect"
})
@Testcontainers
class EnrollmentConcurrencyMysqlTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8");

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
        klass.updateStatus(ClassStatus.OPEN);
        klassId = klassRepository.save(klass).getId();
    }

    @Test
    void 동시수강신청_MySQL_정원1명_초과없이_대기열처리() throws InterruptedException {
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
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(errorCount.get()).isEqualTo(0);

        List<ClassRegistration> registrations = registrationRepository.findByKlassId(klassId);
        long pendingCount = registrations.stream()
                .filter(r -> r.getStatus() == RegistrationStatus.PENDING).count();
        long waitlistedCount = registrations.stream()
                .filter(r -> r.getStatus() == RegistrationStatus.WAITLISTED).count();

        assertThat(pendingCount).isEqualTo(1);
        assertThat(waitlistedCount).isEqualTo(threadCount - 1);
    }

    @Test
    void 동시수강신청_MySQL_reservedCount_정원초과없음() throws InterruptedException {
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
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        Klass result = klassRepository.findById(klassId).orElseThrow();
        assertThat(result.getReservedCount()).isLessThanOrEqualTo(result.getCapacity());
    }

    @Test
    void 동시수강신청_MySQL_취소후_대기자_자동승격() throws InterruptedException {
        // 정원 1명에 5명 신청 → 1 PENDING + 4 WAITLISTED
        for (int i = 0; i < 5; i++) {
            enrollmentService.enroll(testUserIds.get(i), klassId);
        }

        long pendingUserId = registrationRepository.findByKlassId(klassId).stream()
                .filter(r -> r.getStatus() == RegistrationStatus.PENDING)
                .findFirst().orElseThrow().getUserId();

        // PENDING 취소 → 대기열 첫 번째가 자동 승격
        enrollmentService.cancel(pendingUserId, klassId);

        List<ClassRegistration> after = registrationRepository.findByKlassId(klassId);
        long newPendingCount = after.stream()
                .filter(r -> r.getStatus() == RegistrationStatus.PENDING).count();
        long cancelledCount = after.stream()
                .filter(r -> r.getStatus() == RegistrationStatus.CANCELLED).count();

        assertThat(newPendingCount).isEqualTo(1);
        assertThat(cancelledCount).isEqualTo(1);

        Klass result = klassRepository.findById(klassId).orElseThrow();
        assertThat(result.getReservedCount()).isEqualTo(1);
    }
}
