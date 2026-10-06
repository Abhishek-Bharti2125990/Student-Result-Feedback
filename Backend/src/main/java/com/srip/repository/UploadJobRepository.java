package com.srip.repository;

import com.srip.domain.UploadJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UploadJobRepository extends JpaRepository<UploadJob, Long> {

    List<UploadJob> findAllByOrderByCreatedAtDesc();

    List<UploadJob> findByUploadedByOrderByCreatedAtDesc(Long uploadedBy);

    /**
     * {@code upload_jobs.uploaded_by} is a non-null foreign key onto
     * {@code users}, so an account that has imported a file cannot be deleted
     * without destroying that import's audit trail.
     */
    long countByUploadedBy(Long uploadedBy);
}
