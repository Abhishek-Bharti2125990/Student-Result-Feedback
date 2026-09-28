package com.srip.batch;

import com.srip.service.FeedbackService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates one feedback document in its own transaction.
 *
 * <p>This indirection exists for a specific reason. The insight step wants to
 * generate every document independently and step over any that fails, but an
 * exception escaping a method that had <em>joined</em> the step's transaction
 * marks that transaction rollback-only. Catching it would then produce an
 * {@code UnexpectedRollbackException} at commit - a failed step, caused by the
 * very handling meant to tolerate the failure.
 *
 * <p>Running each document in a suspended, separate transaction makes the
 * tolerance real: one document failing costs exactly that document.
 */
@Service
public class FeedbackGenerationRunner {

    private final FeedbackService feedback;

    public FeedbackGenerationRunner(FeedbackService feedback) {
        this.feedback = feedback;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateStudentFeedback(Long studentId, Long examId) {
        feedback.studentFeedback(studentId, examId, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateTeacherFeedback(Long examId, String className) {
        feedback.teacherFeedback(examId, className, true);
    }
}
