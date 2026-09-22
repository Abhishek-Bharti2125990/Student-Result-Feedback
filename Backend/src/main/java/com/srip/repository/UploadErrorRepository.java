package com.srip.repository;

import com.srip.domain.UploadError;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UploadErrorRepository extends JpaRepository<UploadError, Long> {

    List<UploadError> findByUploadJobIdOrderByLineNumberAsc(Long uploadJobId);

    long countByUploadJobId(Long uploadJobId);
}
