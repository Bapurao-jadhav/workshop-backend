package com.workshop.service;

import com.workshop.dto.AdminRegistrationResponse;
import com.workshop.dto.PageResponse;
import com.workshop.dto.RegistrationResponse;
import com.workshop.entity.Registration;
import com.workshop.entity.RegistrationStatus;
import com.workshop.entity.User;
import com.workshop.entity.Workshop;
import com.workshop.entity.WorkshopStatus;
import com.workshop.exception.BusinessRuleException;
import com.workshop.exception.ConflictException;
import com.workshop.exception.ResourceNotFoundException;
import com.workshop.repository.RegistrationRepository;
import com.workshop.repository.UserRepository;
import com.workshop.repository.WorkshopRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RegistrationService {

    private final RegistrationRepository registrationRepository;
    private final WorkshopRepository workshopRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RegistrationService(RegistrationRepository registrationRepository,
                               WorkshopRepository workshopRepository,
                               UserRepository userRepository,
                               ApplicationEventPublisher eventPublisher,
                               Clock clock) {
        this.registrationRepository = registrationRepository;
        this.workshopRepository = workshopRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Registers a user. The workshop row is locked (SELECT ... FOR UPDATE) for the duration of the transaction, so
     * the "seats left" check and the insert are atomic with respect to other registrations for the same workshop.
     * The unique (user_id, workshop_id) constraint is the last line of defence against duplicates. The
     * confirmation email is sent only after the transaction commits and can never roll it back.
     */
    @Transactional
    public RegistrationResponse register(Long userId, Long workshopId) {
        Workshop workshop = workshopRepository.findByIdForUpdate(workshopId)
                .orElseThrow(() -> new ResourceNotFoundException("Workshop not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        LocalDateTime now = LocalDateTime.now(clock);
        if (workshop.getStatus() == WorkshopStatus.CANCELLED) {
            throw new BusinessRuleException("This workshop has been cancelled");
        }
        if (workshop.getStatus() != WorkshopStatus.PUBLISHED) {
            throw new BusinessRuleException("This workshop is not open for registration");
        }
        if (now.isAfter(workshop.getRegistrationDeadline())) {
            throw new BusinessRuleException("The registration deadline for this workshop has passed");
        }

        Optional<Registration> existing = registrationRepository.findByUserIdAndWorkshopId(userId, workshopId);
        if (existing.isPresent() && existing.get().getStatus() == RegistrationStatus.CONFIRMED) {
            throw new ConflictException("You are already registered for this workshop");
        }

        long confirmed = registrationRepository.countByWorkshopIdAndStatus(workshopId, RegistrationStatus.CONFIRMED);
        if (confirmed >= workshop.getCapacity()) {
            throw new ConflictException("This workshop is full");
        }

        // A previously cancelled registration is re-activated so the unique constraint keeps holding.
        Registration registration = existing.orElseGet(Registration::new);
        registration.setUser(user);
        registration.setWorkshop(workshop);
        registration.setStatus(RegistrationStatus.CONFIRMED);
        registration.setRegisteredAt(Instant.now(clock));
        registration.setCancelledAt(null);
        registrationRepository.saveAndFlush(registration);

        eventPublisher.publishEvent(new RegistrationConfirmedEvent(
                registration.getId(), user.getName(), user.getEmail(), workshop.getTitle(),
                workshop.getWorkshopDate(), workshop.getStartTime(), workshop.getEndTime(),
                workshop.getVenue(), workshop.getMeetingLink()));

        return RegistrationResponse.from(registration, isCancellable(registration, now));
    }

    @Transactional(readOnly = true)
    public PageResponse<RegistrationResponse> listMine(Long userId, RegistrationStatus status, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now(clock);
        return PageResponse.of(
                registrationRepository.findAll(filter(userId, null, status, null), pageable),
                r -> RegistrationResponse.from(r, isCancellable(r, now)));
    }

    @Transactional(readOnly = true)
    public RegistrationResponse getMine(Long userId, Long registrationId) {
        Registration r = findOwned(userId, registrationId);
        return RegistrationResponse.from(r, isCancellable(r, LocalDateTime.now(clock)));
    }

    @Transactional
    public RegistrationResponse cancelMine(Long userId, Long registrationId) {
        Registration r = findOwned(userId, registrationId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (r.getStatus() == RegistrationStatus.CANCELLED) {
            throw new BusinessRuleException("This registration is already cancelled");
        }
        if (!now.isBefore(r.getWorkshop().startsAt())) {
            throw new BusinessRuleException("A registration cannot be cancelled once the workshop has started");
        }
        r.setStatus(RegistrationStatus.CANCELLED);
        r.setCancelledAt(Instant.now(clock));
        registrationRepository.saveAndFlush(r);
        return RegistrationResponse.from(r, false);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminRegistrationResponse> listParticipants(Long workshopId, RegistrationStatus status,
                                                                   String q, Pageable pageable) {
        if (!workshopRepository.existsById(workshopId)) {
            throw new ResourceNotFoundException("Workshop not found");
        }
        return PageResponse.of(registrationRepository.findAll(filter(null, workshopId, status, q), pageable),
                AdminRegistrationResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminRegistrationResponse> listAll(Long workshopId, RegistrationStatus status, String q,
                                                          Pageable pageable) {
        return PageResponse.of(registrationRepository.findAll(filter(null, workshopId, status, q), pageable),
                AdminRegistrationResponse::from);
    }

    // ------------------------------------------------------------------ helpers

    private Registration findOwned(Long userId, Long registrationId) {
        // Someone else's registration is reported as 404 so ids cannot be probed.
        return registrationRepository.findByIdAndUserId(registrationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found"));
    }

    private static boolean isCancellable(Registration r, LocalDateTime now) {
        return r.getStatus() == RegistrationStatus.CONFIRMED
                && r.getWorkshop().getStatus() != WorkshopStatus.CANCELLED
                && now.isBefore(r.getWorkshop().startsAt());
    }

    private static Specification<Registration> filter(Long userId, Long workshopId, RegistrationStatus status,
                                                      String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (workshopId != null) {
                predicates.add(cb.equal(root.get("workshop").get("id"), workshopId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                Join<Registration, User> user = root.join("user");
                predicates.add(cb.or(
                        cb.like(cb.lower(user.get("name")), like),
                        cb.like(cb.lower(user.get("email")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
