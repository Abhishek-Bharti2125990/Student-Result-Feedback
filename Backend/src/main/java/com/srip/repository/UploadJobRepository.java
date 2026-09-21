package com.srip.repository;

import com.srip.domain.UploadJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UploadJobRepository extends JpaRepository<UploadJob, Long> {

    List<UploadJob> findAllByOrderByCreatedAtDesc();

    List<UploadJob> findByUploadedByOrderByCreatedAtDesc(Long uploadedBy);
}
