package com.workshop.repository;

import com.workshop.entity.ContactSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ContactSubmissionRepository
        extends JpaRepository<ContactSubmission, Long>, JpaSpecificationExecutor<ContactSubmission> {
}
