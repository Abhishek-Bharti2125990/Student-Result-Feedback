package com.srip.controller;

import com.srip.analytics.ScoreCategory;
import com.srip.dto.dashboard.DashboardDtos.StudentCategoryCard;
import com.srip.dto.dashboard.DashboardDtos.TeacherDashboard;
import com.srip.service.TeacherDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The teacher's view. TEACHER and ADMIN only, enforced by the URL rule on
 * {@code /api/teacher/**}.
 *
 * <p>There is deliberately no "list all students" endpoint here. The dashboard
 * is organised by score category because that is the question a teacher opens it
 * with - who needs help, and how many - and a flat roster of forty names answers
 * nothing. The four category routes are the drill-down.
 *
 * <p>{@code className} and {@code examId} are optional. With one class of
 * results loaded, which is the common case mid-demo and after a single upload,
 * neither has to be supplied.
 */
@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    private final TeacherDashboardService dashboardService;

    public TeacherController(TeacherDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public TeacherDashboard dashboard(@RequestParam(required = false) String className,
                                      @RequestParam(required = false) Long examId) {
        return dashboardService.dashboard(className, examId);
    }

    /** Students below 50%. */
    @GetMapping("/students/critical")
    public List<StudentCategoryCard> critical(@RequestParam(required = false) String className,
                                              @RequestParam(required = false) Long examId) {
        return dashboardService.studentsInCategory(ScoreCategory.CRITICAL, className, examId);
    }

    /** Students between 50 and 70. */
    @GetMapping("/students/average")
    public List<StudentCategoryCard> average(@RequestParam(required = false) String className,
                                             @RequestParam(required = false) Long examId) {
        return dashboardService.studentsInCategory(ScoreCategory.AVERAGE, className, examId);
    }

    /** Students between 70 and 85. */
    @GetMapping("/students/good")
    public List<StudentCategoryCard> good(@RequestParam(required = false) String className,
                                          @RequestParam(required = false) Long examId) {
        return dashboardService.studentsInCategory(ScoreCategory.GOOD, className, examId);
    }

    /** Students above 85. */
    @GetMapping("/students/excellent")
    public List<StudentCategoryCard> excellent(@RequestParam(required = false) String className,
                                               @RequestParam(required = false) Long examId) {
        return dashboardService.studentsInCategory(ScoreCategory.EXCELLENT, className, examId);
    }
}
