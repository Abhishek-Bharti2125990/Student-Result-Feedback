package com.srip.controller;

import com.srip.dto.ai.FeedbackDtos.FeedbackEnvelope;
import com.srip.service.AccessGuard;
import com.srip.service.FeedbackService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI-generated feedback.
 *
 * <p>Each route returns the stored document if one exists, so repeated reads
 * are free and everybody sees the same words. {@code ?refresh=true} forces
 * regeneration, which is a billed model call - that is why it is an explicit
 * opt-in rather than the default.
 *
 * <p>Every response carries {@code source}: {@code CLAUDE} when the model wrote
 * it, {@code FALLBACK} when the local rule-based writer did. Readers should
 * never have to guess which they are looking at.
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedback;
    private final AccessGuard accessGuard;

    public FeedbackController(FeedbackService feedback, AccessGuard accessGuard) {
        this.feedback = feedback;
        this.accessGuard = accessGuard;
    }

    // -- Student-facing ------------------------------------------------------

    @GetMapping("/student/{studentId}/exams/{examId}")
    public FeedbackEnvelope studentFeedback(@PathVariable Long studentId,
                                            @PathVariable Long examId,
                                            @RequestParam(defaultValue = "false") boolean refresh) {
        accessGuard.assertCanReadStudent(studentId);
        return feedback.studentFeedback(studentId, examId, refresh);
    }

    @GetMapping("/me/exams/{examId}")
    public FeedbackEnvelope myFeedback(@PathVariable Long examId,
                                       @RequestParam(defaultValue = "false") boolean refresh) {
        return feedback.studentFeedback(accessGuard.currentStudentIdOrFail(), examId, refresh);
    }

    // -- Parent-facing -------------------------------------------------------

    @GetMapping("/parent/{studentId}/exams/{examId}")
    public FeedbackEnvelope parentFeedback(@PathVariable Long studentId,
                                           @PathVariable Long examId,
                                           @RequestParam(defaultValue = "false") boolean refresh) {
        accessGuard.assertCanReadStudent(studentId);
        return feedback.parentFeedback(studentId, examId, refresh);
    }

    // -- Teacher-facing (TEACHER and ADMIN only, enforced by URL rule) -------

    @GetMapping("/teacher/class/{className}/exams/{examId}")
    public FeedbackEnvelope teacherFeedback(@PathVariable String className,
                                            @PathVariable Long examId,
                                            @RequestParam(defaultValue = "false") boolean refresh) {
        return feedback.teacherFeedback(examId, className, refresh);
    }
}
