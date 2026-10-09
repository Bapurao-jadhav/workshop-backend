package com.workshop.service;

import com.workshop.dto.ContactRequest;
import com.workshop.dto.ContactResponse;
import com.workshop.dto.PageResponse;
import com.workshop.entity.ContactStatus;
import com.workshop.entity.ContactSubmission;
import com.workshop.exception.ResourceNotFoundException;
import com.workshop.repository.ContactSubmissionRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ContactService {

    private final ContactSubmissionRepository repository;

    public ContactService(ContactSubmissionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ContactResponse submit(ContactRequest request) {
        ContactSubmission c = new ContactSubmission();
        c.setName(request.name().trim());
        c.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        c.setPhone(StringUtils.hasText(request.phone()) ? request.phone().trim() : null);
        c.setSubject(request.subject().trim());
        c.setMessage(request.message().trim());
        c.setStatus(ContactStatus.NEW);
        repository.saveAndFlush(c);
        return ContactResponse.from(c);
    }

    @Transactional(readOnly = true)
    public PageResponse<ContactResponse> list(ContactStatus status, String q, Pageable pageable) {
        Specification<ContactSubmission> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("subject")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return PageResponse.of(repository.findAll(spec, pageable), ContactResponse::from);
    }

    @Transactional(readOnly = true)
    public ContactResponse get(Long id) {
        return ContactResponse.from(find(id));
    }

    @Transactional
    public ContactResponse updateStatus(Long id, ContactStatus status) {
        ContactSubmission c = find(id);
        c.setStatus(status);
        repository.saveAndFlush(c);
        return ContactResponse.from(c);
    }

    private ContactSubmission find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contact submission not found"));
    }
}
