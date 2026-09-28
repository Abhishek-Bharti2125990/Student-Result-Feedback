package com.srip.controller;

import com.srip.dto.ai.FeedbackDtos.FeedbackEnvelope;
import com.srip.dto.dashboard.DashboardDtos.ResourceSuggestion;
import com.srip.dto.dashboard.DashboardDtos.StudentDashboard;
import com.srip.service.AccessGuard;
import com.srip.service.StudentDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The student's own view of their results.
 *
 * <p>No student id in any path. The caller's id comes from their token, which
 * removes both the round trip a client would need to discover it and the
 * temptation to trust one the client supplied.
 *
 * <p>Staff may pass {@code ?studentId=} to look at one student's card - a
 * teacher discussing a result with a student needs exactly this screen. The
 * parameter is checked by {@link AccessGuard}, so a student passing someone
 * else's id gets 403 rather than their data.
 *
 * <p>{@code examId} is optional everywhere; omitted, it means the most recent
 * exam, which is what "how did I do?" almost always means.
 */
@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final StudentDashboardService dashboardService;
    private final AccessGuard accessGuard;

    public StudentController(StudentDashboardService dashboardService, AccessGuard accessGuard) {
        this.dashboardService = dashboardService;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/dashboard")
    public StudentDashboard dashboard(@RequestParam(required = false) Long studentId,
                                      @RequestParam(required = false) Long examId) {
        return dashboardService.dashboard(resolveStudentId(studentId), examId);
    }

    /**
     * @param refresh regenerates the document; a billed model call, so it is an
     *                explicit opt-in rather than the default
     */
    @GetMapping("/feedback")
    public FeedbackEnvelope feedback(@RequestParam(required = false) Long studentId,
                                     @RequestParam(required = false) Long examId,
                                     @RequestParam(defaultValue = "false") boolean refresh) {
        return dashboardService.studentFeedback(resolveStudentId(studentId), examId, refresh);
    }

    @GetMapping("/resources")
    public List<ResourceSuggestion> resources(@RequestParam(required = false) Long studentId,
                                              @RequestParam(required = false) Long examId) {
        return dashboardService.resources(resolveStudentId(studentId), examId);
    }

    /**
     * @return the caller's own student record, or the requested one once the
     *         guard has confirmed the caller may read it
     */
    private Long resolveStudentId(Long requestedStudentId) {
        if (requestedStudentId == null) {
            return accessGuard.currentStudentIdOrFail();
        }
        accessGuard.assertCanReadStudent(requestedStudentId);
        return requestedStudentId;
    }
}
