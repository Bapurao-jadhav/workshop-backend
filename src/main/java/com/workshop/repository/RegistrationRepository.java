package com.workshop.repository;

import com.workshop.entity.Registration;
import com.workshop.entity.RegistrationStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationRepository
        extends JpaRepository<Registration, Long>, JpaSpecificationExecutor<Registration> {

    Optional<Registration> findByUserIdAndWorkshopId(Long userId, Long workshopId);

    @EntityGraph(attributePaths = {"workshop", "user"})
    Optional<Registration> findByIdAndUserId(Long id, Long userId);

    long countByWorkshopIdAndStatus(Long workshopId, RegistrationStatus status);

    @Override
    @EntityGraph(attributePaths = {"workshop", "user"})
    Page<Registration> findAll(Specification<Registration> spec, Pageable pageable);

    @Query("select r.workshop.id, count(r) from Registration r "
            + "where r.workshop.id in :ids and r.status = :status group by r.workshop.id")
    List<Object[]> countByWorkshopIds(@Param("ids") Collection<Long> ids,
                                      @Param("status") RegistrationStatus status);

    @Modifying(flushAutomatically = true)
    @Query("update Registration r set r.status = :cancelled, r.cancelledAt = :now "
            + "where r.workshop.id = :workshopId and r.status = :confirmed")
    int cancelAllForWorkshop(@Param("workshopId") Long workshopId,
                             @Param("now") Instant now,
                             @Param("cancelled") RegistrationStatus cancelled,
                             @Param("confirmed") RegistrationStatus confirmed);
}
