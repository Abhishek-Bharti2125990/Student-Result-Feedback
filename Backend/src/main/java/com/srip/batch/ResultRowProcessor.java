package com.srip.batch;

import com.srip.analytics.GradingService;
import com.srip.domain.Exam;
import com.srip.domain.ExamResult;
import com.srip.domain.Student;
import com.srip.domain.Subject;
import com.srip.domain.Topic;
import com.srip.domain.TopicScore;
import com.srip.repository.ExamRepository;
import com.srip.repository.ExamResultRepository;
import com.srip.repository.StudentRepository;
import com.srip.repository.SubjectRepository;
import com.srip.repository.TopicRepository;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates a row and turns it into a persistable {@link ExamResult}.
 *
 * <p>Validation happens here rather than in the reader so a rejected row can be
 * reported with its line number and a specific reason. Three kinds of check
 * run: presence and numeric format of each column, referential existence of the
 * student, exam and subject codes, and arithmetic sanity - marks cannot exceed
 * the maximum, and the maximum cannot be zero.
 *
 * <p>Reference lookups are memoised as <em>identifiers</em>, never as entities.
 * A 900-row file covering one class names the same handful of exams and
 * subjects on every line, so memoising turns thousands of queries into a few
 * dozen - but a chunk-oriented step commits every chunk, so an entity cached in
 * this singleton would be detached by the next chunk's transaction. Caching the
 * id and calling {@code getReferenceById} inside the current transaction avoids
 * that entirely. Failed lookups are not cached, so adding a missing subject and
 * re-uploading works without a restart.
 *
 * <p>An existing result for the same student, exam and subject is loaded and
 * updated rather than duplicated: re-uploading a corrected file is a normal
 * operation, and it should correct the mark, not create a second one.
 */
@Component
public class ResultRowProcessor implements ItemProcessor<ResultCsvRow, ExamResult> {

    private final StudentRepository students;
    private final ExamRepository exams;
    private final SubjectRepository subjects;
    private final TopicRepository topics;
    private final ExamResultRepository examResults;
    private final GradingService grading;

    private final Map<String, Long> studentIds = new ConcurrentHashMap<>();
    private final Map<String, Long> examIds = new ConcurrentHashMap<>();
    private final Map<String, Long> subjectIds = new ConcurrentHashMap<>();
    private final Map<String, Long> topicIds = new ConcurrentHashMap<>();

    public ResultRowProcessor(StudentRepository students,
                              ExamRepository exams,
                              SubjectRepository subjects,
                              TopicRepository topics,
                              ExamResultRepository examResults,
                              GradingService grading) {
        this.students = students;
        this.exams = exams;
        this.subjects = subjects;
        this.topics = topics;
        this.examResults = examResults;
        this.grading = grading;
    }

    @Override
    public ExamResult process(ResultCsvRow row) {
        Long studentId = resolveStudentId(row);
        Long examId = resolveExamId(row);
        Long subjectId = resolveSubjectId(row);

        BigDecimal marks = requireDecimal(row, row.marksObtained(), "marks_obtained");
        BigDecimal maxMarks = requireDecimal(row, row.maxMarks(), "max_marks");

        if (maxMarks.signum() <= 0) {
            throw RowValidationException.of(row,
                    "max_marks must be greater than zero, found " + maxMarks.toPlainString());
        }
        if (marks.signum() < 0) {
            throw RowValidationException.of(row,
                    "marks_obtained cannot be negative, found " + marks.toPlainString());
        }
        if (marks.compareTo(maxMarks) > 0) {
            throw RowValidationException.of(row, "marks_obtained (%s) exceeds max_marks (%s)"
                    .formatted(marks.toPlainString(), maxMarks.toPlainString()));
        }

        ExamResult result = examResults
                .findByStudentIdAndExamIdAndSubjectId(studentId, examId, subjectId)
                .orElseGet(() -> new ExamResult(
                        students.getReferenceById(studentId),
                        exams.getReferenceById(examId),
                        subjects.getReferenceById(subjectId)));

        BigDecimal percentage = grading.percentage(marks, maxMarks);
        result.setMarksObtained(marks);
        result.setMaxMarks(maxMarks);
        result.setPercentage(percentage);
        result.setGrade(grading.grade(percentage));
        result.setAttempted(parseAttempted(row));
        result.setRemarks(row.remarks());

        applyTopicScores(row, subjectId, maxMarks, result);
        return result;
    }

    private void applyTopicScores(ResultCsvRow row, Long subjectId, BigDecimal subjectMax, ExamResult result) {
        if (row.topicMarks().isEmpty()) {
            return;
        }

        // Re-uploading replaces the breakdown rather than appending to it.
        result.clearTopicScores();

        BigDecimal topicMaxTotal = BigDecimal.ZERO;
        for (ResultCsvRow.TopicMark topicMark : row.topicMarks()) {
            Long topicId = resolveTopicId(row, subjectId, topicMark.topicName());

            BigDecimal got = requireDecimal(row, topicMark.marksObtained(),
                    "topic '" + topicMark.topicName() + "' marks");
            BigDecimal max = requireDecimal(row, topicMark.maxMarks(),
                    "topic '" + topicMark.topicName() + "' maximum");

            if (max.signum() <= 0) {
                throw RowValidationException.of(row,
                        "Topic '%s' maximum must be greater than zero".formatted(topicMark.topicName()));
            }
            if (got.compareTo(max) > 0) {
                throw RowValidationException.of(row, "Topic '%s' marks (%s) exceed its maximum (%s)"
                        .formatted(topicMark.topicName(), got.toPlainString(), max.toPlainString()));
            }

            topicMaxTotal = topicMaxTotal.add(max);
            Topic topic = topics.getReferenceById(topicId);
            result.addTopicScore(new TopicScore(topic, got, max, grading.percentage(got, max)));
        }

        // A breakdown totalling more than the paper means the file is wrong,
        // not that the student scored more than was available.
        if (topicMaxTotal.compareTo(subjectMax) > 0) {
            throw RowValidationException.of(row, "Topic maximums total %s, which exceeds max_marks of %s"
                    .formatted(topicMaxTotal.toPlainString(), subjectMax.toPlainString()));
        }
    }

    private Long resolveStudentId(ResultCsvRow row) {
        requireText(row, row.admissionNo(), "admission_no");
        Long id = studentIds.computeIfAbsent(row.admissionNo(),
                code -> students.findByAdmissionNo(code).map(Student::getId).orElse(null));
        if (id == null) {
            throw RowValidationException.of(row, "Unknown admission_no '" + row.admissionNo() + "'");
        }
        return id;
    }

    private Long resolveExamId(ResultCsvRow row) {
        requireText(row, row.examCode(), "exam_code");
        Long id = examIds.computeIfAbsent(row.examCode(),
                code -> exams.findByCode(code).map(Exam::getId).orElse(null));
        if (id == null) {
            throw RowValidationException.of(row, "Unknown exam_code '" + row.examCode() + "'");
        }
        return id;
    }

    private Long resolveSubjectId(ResultCsvRow row) {
        requireText(row, row.subjectCode(), "subject_code");
        Long id = subjectIds.computeIfAbsent(row.subjectCode(),
                code -> subjects.findByCode(code).map(Subject::getId).orElse(null));
        if (id == null) {
            throw RowValidationException.of(row, "Unknown subject_code '" + row.subjectCode() + "'");
        }
        return id;
    }

    private Long resolveTopicId(ResultCsvRow row, Long subjectId, String topicName) {
        String key = subjectId + "::" + topicName.toLowerCase();
        Long id = topicIds.computeIfAbsent(key,
                ignored -> topics.findBySubjectIdAndNameIgnoreCase(subjectId, topicName)
                        .map(Topic::getId)
                        .orElse(null));
        if (id == null) {
            throw RowValidationException.of(row, "Topic '%s' is not defined for subject '%s'"
                    .formatted(topicName, row.subjectCode()));
        }
        return id;
    }

    private boolean parseAttempted(ResultCsvRow row) {
        if (row.attempted() == null) {
            return true;
        }
        return switch (row.attempted().toLowerCase()) {
            case "true", "yes", "y", "1" -> true;
            case "false", "no", "n", "0" -> false;
            default -> throw RowValidationException.of(row,
                    "attempted must be true or false, found '" + row.attempted() + "'");
        };
    }

    private static BigDecimal requireDecimal(ResultCsvRow row, String value, String column) {
        requireText(row, value, column);
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw RowValidationException.of(row, "%s must be a number, found '%s'".formatted(column, value));
        }
    }

    private static void requireText(ResultCsvRow row, String value, String column) {
        if (value == null || value.isBlank()) {
            throw RowValidationException.of(row, column + " is required but was blank");
        }
    }
}
