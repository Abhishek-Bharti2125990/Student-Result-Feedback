package com.srip.repository;

import com.srip.domain.ParentStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ParentStudentRepository extends JpaRepository<ParentStudent, Long> {

    @Query("select l from ParentStudent l join fetch l.student where l.parent.id = :parentUserId")
    List<ParentStudent> findByParentUserId(@Param("parentUserId") Long parentUserId);

    /** The PARENT role authorisation check. */
    boolean existsByParentIdAndStudentId(Long parentId, Long studentId);
}
