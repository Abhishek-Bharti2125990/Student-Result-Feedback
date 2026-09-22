package com.srip.repository;

import com.srip.domain.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    Optional<Topic> findBySubjectIdAndNameIgnoreCase(Long subjectId, String name);

    List<Topic> findBySubjectId(Long subjectId);
}
