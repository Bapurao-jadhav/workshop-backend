package com.workshop.service;

import com.workshop.dto.AdminRegistrationResponse;
import com.workshop.dto.PageResponse;
import com.workshop.dto.RegistrationFormResponse;
import com.workshop.dto.RegistrationResponse;
import com.workshop.entity.Registration;
import com.workshop.entity.RegistrationStatus;
import com.workshop.entity.SubmissionSource;
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
     * The unique (user_id, workshop_id) constraint is the last line of defence against duplicates. New registrations
     * remain PENDING until an administrator confirms or rejects them; the matching email is sent after that decision commits.
     */
    @Transactional
    public RegistrationResponse register(Long userId, Long workshopId) {
        return register(userId, workshopId, SubmissionSource.WEB_APP);
    }

    /** Step 1 of the flow: tells the user which Google Form to fill in. Nothing is created yet. */
    @Transactional(readOnly = true)
    public RegistrationFormResponse getRegistrationForm(Long userId, Long workshopId) {
        Workshop workshop = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new ResourceNotFoundException("Workshop not found"));
        if (workshop.getStatus() == WorkshopStatus.CANCELLED) {
            throw new BusinessRuleException("This workshop has been cancelled");
        }
        if (workshop.getStatus() != WorkshopStatus.PUBLISHED) {
            throw new BusinessRuleException("This workshop is not open for registration");
        }
        if (LocalDateTime.now(clock).isAfter(workshop.getRegistrationDeadline())) {
            throw new BusinessRuleException("The registration deadline for this workshop has passed");
        }
        if (!StringUtils.hasText(workshop.getRegistrationFormUrl())) {
            throw new BusinessRuleException("This workshop has no registration form yet");
        }
        Optional<Registration> existing = registrationRepository.findByUserIdAndWorkshopId(userId, workshopId);
        if (existing.isPresent() && (existing.get().getStatus() == RegistrationStatus.CONFIRMED
                || existing.get().getStatus() == RegistrationStatus.PENDING)) {
            throw new ConflictException("You are already registered or have a pending registration for this workshop");
        }
        long active = registrationRepository.countByWorkshopIdAndStatusIn(workshopId,
                List.of(RegistrationStatus.PENDING, RegistrationStatus.CONFIRMED));
        if (active >= workshop.getCapacity()) {
            throw new ConflictException("This workshop is full");
        }
        return new RegistrationFormResponse(workshop.getId(), workshop.getTitle(), workshop.getRegistrationFormUrl(),
                "Fill in the form using the same email as your account. Your request is sent to the admin "
                        + "automatically; if it does not show up under your registrations, come back and confirm "
                        + "that you submitted the form.");
    }

    @Transactional
    public RegistrationResponse register(Long userId, Long workshopId, SubmissionSource source) {
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
        if (existing.isPresent() && (existing.get().getStatus() == RegistrationStatus.CONFIRMED
                || existing.get().getStatus() == RegistrationStatus.PENDING)) {
            throw new ConflictException("You are already registered or have a pending registration for this workshop");
        }

        long activeRegistrations = registrationRepository.countByWorkshopIdAndStatusIn(workshopId,
                List.of(RegistrationStatus.PENDING, RegistrationStatus.CONFIRMED));
        if (activeRegistrations >= workshop.getCapacity()) {
            throw new ConflictException("This workshop is full");
        }

        // A previously cancelled registration is re-activated so the unique constraint keeps holding.
        Registration registration = existing.orElseGet(Registration::new);
        registration.setUser(user);
        registration.setWorkshop(workshop);
        registration.setStatus(RegistrationStatus.PENDING);
        registration.setSubmissionSource(source);
        registration.setRegisteredAt(Instant.now(clock));
        registration.setCancelledAt(null);
        registrationRepository.saveAndFlush(registration);

        // Confirmation/rejection emails are sent only after an admin decides the registration status.
        return RegistrationResponse.from(registration, isCancellable(registration, now));
    }

    @Transactional
    public AdminRegistrationResponse decideRegistration(Long registrationId, RegistrationStatus requestedStatus) {
        if (requestedStatus != RegistrationStatus.CONFIRMED && requestedStatus != RegistrationStatus.REJECTED) {
            throw new BusinessRuleException("Admin can only set registration status to CONFIRMED or REJECTED");
        }

        // Lock the workshop first, matching the lock order used by new registrations and preventing overbooking.
        Long workshopId = registrationRepository.findWorkshopIdByRegistrationId(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found"));
        Workshop workshop = workshopRepository.findByIdForUpdate(workshopId)
                .orElseThrow(() -> new ResourceNotFoundException("Workshop not found"));
        // Load the registration after acquiring the workshop lock so a concurrent admin decision is observed.
        Registration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found"));
        registration.setWorkshop(workshop);

        if (registration.getStatus() != RegistrationStatus.PENDING) {
            throw new BusinessRuleException("Only PENDING registrations can be approved or rejected");
        }

        if (requestedStatus == RegistrationStatus.CONFIRMED) {
            long confirmed = registrationRepository.countByWorkshopIdAndStatus(
                    workshop.getId(), RegistrationStatus.CONFIRMED);
            if (confirmed >= workshop.getCapacity()) {
                throw new ConflictException("This workshop is full; the registration cannot be confirmed");
            }
        }

        registration.setStatus(requestedStatus);
        registration.setCancelledAt(null);
        registrationRepository.saveAndFlush(registration);

        User user = registration.getUser();
        if (requestedStatus == RegistrationStatus.CONFIRMED) {
            eventPublisher.publishEvent(new RegistrationConfirmedEvent(
                    registration.getId(), user.getName(), user.getEmail(), workshop.getTitle(),
                    workshop.getWorkshopDate(), workshop.getStartTime(), workshop.getEndTime(),
                    workshop.getVenue(), workshop.getMeetingLink()));
        } else {
            eventPublisher.publishEvent(new RegistrationRejectedEvent(
                    registration.getId(), user.getName(), user.getEmail(), workshop.getTitle(),
                    workshop.getWorkshopDate(), workshop.getStartTime(), workshop.getEndTime(), workshop.getVenue()));
        }
        return AdminRegistrationResponse.from(registration);
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
        return (r.getStatus() == RegistrationStatus.PENDING || r.getStatus() == RegistrationStatus.CONFIRMED)
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
    @Transactional
    public RegistrationResponse requestRegistrationByEmail(
            Long workshopId,
            String email,
            SubmissionSource source) {

        if (!StringUtils.hasText(email)) {
            throw new BusinessRuleException("Email is required");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No user found with email: " + normalizedEmail
                        ));

        return register(user.getId(), workshopId, source == null ? SubmissionSource.FORM_WEBHOOK : source);
    }
   
}
