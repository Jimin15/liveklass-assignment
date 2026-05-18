package com.example.liveklass.klass;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KlassRepository extends JpaRepository<Klass, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT k FROM Klass k WHERE k.id = :id")
    Optional<Klass> findByIdWithLock(@Param("id") Long id);

    List<Klass> findByStatus(ClassStatus status);

    List<Klass> findByStatusIn(List<ClassStatus> statuses);
}
