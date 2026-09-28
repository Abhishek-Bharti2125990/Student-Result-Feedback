package com.srip.repository;

import com.srip.domain.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    Optional<Topic> findBySubjectIdAndNameIgnoreCase(Long subjectId, String name);

    List<Topic> findBySubjectId(Long subjectId);

    /** Topics with their subject joined, for turning topic ids back into labels. */
    @Query("""
            select t from Topic t
            join fetch t.subject
            where t.id in :ids
            """)
    List<Topic> findAllWithSubjectByIdIn(@Param("ids") List<Long> ids);
}
