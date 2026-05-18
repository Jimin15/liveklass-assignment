package com.example.liveklass.enrollment;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.Optional;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    Optional<WaitlistEntry> findByRegistrationId(Long registrationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    Optional<WaitlistEntry> findFirstByKlassIdAndStatusOrderBySequenceAsc(Long klassId, WaitlistStatus status);

    int countByKlassIdAndStatusAndSequenceLessThan(Long klassId, WaitlistStatus status, int sequence);
}
