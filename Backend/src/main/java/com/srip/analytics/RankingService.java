package com.srip.analytics;

import com.srip.domain.Student;
import com.srip.dto.analytics.AnalyticsDtos.RankingEntry;
import com.srip.repository.ExamResultRepository.StudentTotals;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class RankingService {

    private final GradingService gradingService;

    public RankingService(GradingService gradingService) {
        this.gradingService = gradingService;
    }

    /**
     * Ranks a class for one exam from pre-aggregated totals.
     *
     * <p>Ties share a rank and the next rank skips accordingly (1, 2, 2, 4).
     * Two students on identical marks must not be ordered by database id, and
     * inventing a tiebreak would be a silent editorial decision about who did
     * better.
     */
    public List<RankingEntry> rank(List<StudentTotals> totals, Map<Long, Student> studentsById) {
        List<Row> rows = totals.stream()
                .map(total -> {
                    BigDecimal marks = total.getTotalMarks() == null ? BigDecimal.ZERO : total.getTotalMarks();
                    BigDecimal max = total.getTotalMax() == null ? BigDecimal.ZERO : total.getTotalMax();
                    return new Row(total.getStudentId(), marks, max, gradingService.percentage(marks, max));
                })
                .sorted(Comparator.comparing(Row::percentage).reversed()
                        .thenComparing(Row::marks, Comparator.reverseOrder()))
                .toList();

        List<RankingEntry> ranked = new ArrayList<>(rows.size());
        int rank = 0;
        BigDecimal previousPercentage = null;

        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            if (previousPercentage == null || row.percentage().compareTo(previousPercentage) != 0) {
                rank = index + 1;
                previousPercentage = row.percentage();
            }
            Student student = studentsById.get(row.studentId());
            ranked.add(new RankingEntry(
                    rank,
                    row.studentId(),
                    student == null ? null : student.getAdmissionNo(),
                    student == null ? "Unknown" : student.getFullName(),
                    gradingService.scale(row.marks()),
                    gradingService.scale(row.max()),
                    row.percentage(),
                    gradingService.grade(row.percentage())));
        }
        return ranked;
    }

    /** @return the student's rank, or 0 when they did not sit this exam */
    public int rankOf(List<RankingEntry> rankings, Long studentId) {
        return rankings.stream()
                .filter(entry -> entry.studentId().equals(studentId))
                .map(RankingEntry::rank)
                .findFirst()
                .orElse(0);
    }

    private record Row(Long studentId, BigDecimal marks, BigDecimal max, BigDecimal percentage) {
    }
}
