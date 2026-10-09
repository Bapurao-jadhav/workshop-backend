package com.workshop.repository;

import com.workshop.entity.Workshop;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkshopRepository extends JpaRepository<Workshop, Long>, JpaSpecificationExecutor<Workshop> {

    /**
     * Loads a workshop with a row-level write lock (SELECT ... FOR UPDATE). Registrations and workshop edits
     * go through this method so that capacity checks are serialised per workshop.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Workshop w where w.id = :id")
    Optional<Workshop> findByIdForUpdate(@Param("id") Long id);
}
