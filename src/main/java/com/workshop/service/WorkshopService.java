package com.workshop.service;

import com.workshop.dto.PageResponse;
import com.workshop.dto.WorkshopRequest;
import com.workshop.dto.WorkshopResponse;
import com.workshop.entity.RegistrationStatus;
import com.workshop.entity.Workshop;
import com.workshop.entity.WorkshopStatus;
import com.workshop.exception.BadRequestException;
import com.workshop.exception.BusinessRuleException;
import com.workshop.exception.ResourceNotFoundException;
import com.workshop.repository.RegistrationRepository;
import com.workshop.repository.WorkshopRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WorkshopService {

    private final WorkshopRepository workshopRepository;
    private final RegistrationRepository registrationRepository;
    private final Clock clock;

    public WorkshopService(WorkshopRepository workshopRepository, RegistrationRepository registrationRepository,
                           Clock clock) {
        this.workshopRepository = workshopRepository;
        this.registrationRepository = registrationRepository;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ public

    @Transactional(readOnly = true)
    public PageResponse<WorkshopResponse> searchPublished(String q, String category, String audience,
                                                          LocalDate from, LocalDate to, Pageable pageable) {
        Specification<Workshop> spec = filter(q, category, audience, from, to, WorkshopStatus.PUBLISHED);
        Page<Workshop> page = workshopRepository.findAll(spec, pageable);
        return toPage(page, false);
    }

    @Transactional(readOnly = true)
    public WorkshopResponse getPublished(Long id) {
        Workshop w = workshopRepository.findById(id)
                .filter(x -> x.getStatus() == WorkshopStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Workshop not found"));
        return toResponse(w, confirmedCount(w.getId()), false);
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public PageResponse<WorkshopResponse> searchAll(String q, String category, WorkshopStatus status,
                                                    LocalDate from, LocalDate to, Pageable pageable) {
        Page<Workshop> page = workshopRepository.findAll(filter(q, category, null, from, to, status), pageable);
        return toPage(page, true);
    }

    @Transactional(readOnly = true)
    public WorkshopResponse getForAdmin(Long id) {
        Workshop w = workshopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workshop not found"));
        return toResponse(w, confirmedCount(id), true);
    }

    @Transactional
    public WorkshopResponse create(WorkshopRequest request) {
        validateSchedule(request);
        LocalDateTime now = LocalDateTime.now(clock);
        if (request.date().isBefore(now.toLocalDate())) {
            throw new BadRequestException("Workshop date cannot be in the past");
        }
        if (!request.registrationDeadline().isAfter(now)) {
            throw new BadRequestException("Registration deadline must be in the future");
        }
        Workshop w = new Workshop();
        apply(w, request);
        w.setStatus(WorkshopStatus.DRAFT);
        workshopRepository.saveAndFlush(w);
        return toResponse(w, 0, true);
    }

    @Transactional
    public WorkshopResponse update(Long id, WorkshopRequest request) {
        Workshop w = lock(id);
        if (w.getStatus() == WorkshopStatus.CANCELLED) {
            throw new BusinessRuleException("A cancelled workshop cannot be edited");
        }
        validateSchedule(request);
        long confirmed = confirmedCount(id);
        if (request.capacity() < confirmed) {
            throw new BusinessRuleException("Capacity cannot be lower than the current number of participants ("
                    + confirmed + ")");
        }
        apply(w, request);
        workshopRepository.saveAndFlush(w);
        return toResponse(w, confirmed, true);
    }

    @Transactional
    public WorkshopResponse changeStatus(Long id, WorkshopStatus target) {
        Workshop w = lock(id);
        WorkshopStatus current = w.getStatus();
        if (current == WorkshopStatus.CANCELLED) {
            throw new BusinessRuleException("A cancelled workshop cannot be changed");
        }
        if (target != current) {
            switch (target) {
                case PUBLISHED -> {
                    if (!w.startsAt().isAfter(LocalDateTime.now(clock))) {
                        throw new BusinessRuleException("A workshop that has already started cannot be published");
                    }
                    w.setStatus(WorkshopStatus.PUBLISHED);
                }
                case UNPUBLISHED -> {
                    if (current != WorkshopStatus.PUBLISHED) {
                        throw new BusinessRuleException("Only a published workshop can be unpublished");
                    }
                    w.setStatus(WorkshopStatus.UNPUBLISHED);
                }
                case CANCELLED -> {
                    w.setStatus(WorkshopStatus.CANCELLED);
                    registrationRepository.cancelAllForWorkshop(id, Instant.now(clock),
                            RegistrationStatus.CANCELLED, RegistrationStatus.CONFIRMED);
                }
                case DRAFT -> throw new BusinessRuleException("A workshop cannot be moved back to DRAFT");
                default -> throw new BusinessRuleException("Unsupported status");
            }
            workshopRepository.saveAndFlush(w);
        }
        return toResponse(w, confirmedCount(id), true);
    }

    // ------------------------------------------------------------------ helpers

    private Workshop lock(Long id) {
        return workshopRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workshop not found"));
    }

    private void validateSchedule(WorkshopRequest r) {
        if (!r.endTime().isAfter(r.startTime())) {
            throw new BadRequestException("End time must be after start time");
        }
        if (r.registrationDeadline().isAfter(LocalDateTime.of(r.date(), r.startTime()))) {
            throw new BadRequestException("Registration deadline must be on or before the workshop start");
        }
        if (!StringUtils.hasText(r.venue()) && !StringUtils.hasText(r.meetingLink())) {
            throw new BadRequestException("Provide a venue, an online meeting link, or both");
        }
    }

    private void apply(Workshop w, WorkshopRequest r) {
        w.setTitle(r.title().trim());
        w.setDescription(r.description().trim());
        w.setCategory(r.category().trim());
        w.setInstructorName(r.instructorName().trim());
        w.setTargetAudience(r.targetAudience().trim());
        w.setWorkshopDate(r.date());
        w.setStartTime(r.startTime());
        w.setEndTime(r.endTime());
        w.setRegistrationDeadline(r.registrationDeadline());
        w.setVenue(blankToNull(r.venue()));
        w.setMeetingLink(blankToNull(r.meetingLink()));
        w.setCapacity(r.capacity());
        w.setFee(r.fee());
        w.setImageUrl(blankToNull(r.imageUrl()));
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private long confirmedCount(Long workshopId) {
        return registrationRepository.countByWorkshopIdAndStatus(workshopId, RegistrationStatus.CONFIRMED);
    }

    private WorkshopResponse toResponse(Workshop w, long confirmed, boolean includeMeetingLink) {
        boolean open = w.getStatus() == WorkshopStatus.PUBLISHED
                && LocalDateTime.now(clock).isBefore(w.getRegistrationDeadline())
                && confirmed < w.getCapacity();
        return WorkshopResponse.from(w, confirmed, open, includeMeetingLink);
    }

    private PageResponse<WorkshopResponse> toPage(Page<Workshop> page, boolean admin) {
        Map<Long, Long> counts = new HashMap<>();
        List<Long> ids = page.getContent().stream().map(Workshop::getId).toList();
        if (!ids.isEmpty()) {
            for (Object[] row : registrationRepository.countByWorkshopIds(ids, RegistrationStatus.CONFIRMED)) {
                counts.put((Long) row[0], (Long) row[1]);
            }
        }
        return PageResponse.of(page, w -> toResponse(w, counts.getOrDefault(w.getId(), 0L), admin));
    }

    private static Specification<Workshop> filter(String q, String category, String audience, LocalDate from,
                                                  LocalDate to, WorkshopStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("instructorName")), like)));
            }
            if (StringUtils.hasText(category)) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(audience)) {
                predicates.add(cb.like(cb.lower(root.get("targetAudience")),
                        "%" + audience.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("workshopDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("workshopDate"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
