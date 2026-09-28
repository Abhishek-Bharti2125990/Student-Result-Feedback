package com.srip.batch;

import com.srip.domain.Exam;
import com.srip.domain.Student;
import com.srip.domain.Subject;
import com.srip.domain.Topic;
import com.srip.repository.ExamRepository;
import com.srip.repository.StudentRepository;
import com.srip.repository.SubjectRepository;
import com.srip.repository.TopicRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Validates a row and resolves its names to identifiers.
 *
 * <p>Validation happens here rather than in the reader so a rejected row can be
 * reported with its line number and a specific reason. Three kinds of check
 * run: presence of each column, numeric format of the two mark columns, and
 * arithmetic sanity - marks cannot exceed the maximum, and the maximum cannot be
 * zero or negative.
 *
 * <p>Reference lookups are memoised as <em>identifiers</em>, never as entities.
 * A 900-row file covering one class names the same handful of exams, subjects and
 * topics on every line, so memoising turns thousands of queries into a few dozen
 * - but a chunk-oriented step commits every chunk, so an entity cached across
 * chunks would be detached by the next chunk's transaction.
 *
 * <p>Step-scoped, so the memo lives exactly as long as one import. A singleton
 * cache would outlive the reference data it describes and hand the writer ids
 * that no longer exist.
 *
 * <p>Nothing is created here. {@link ReferenceDataTasklet} has already committed
 * every student, exam, subject and topic the file mentions, so a lookup that
 * misses means the row itself is unusable - a blank subject, or a name that
 * differs from the one the reference step saw.
 */
@Component
@StepScope
public class ResultRowProcessor implements ItemProcessor<ResultCsvRow, TopicResultRow> {

    private static final int SUBJECT_CODE_LENGTH = 32;
    private static final int EXAM_CODE_LENGTH = 64;

    private final StudentRepository students;
    private final ExamRepository exams;
    private final SubjectRepository subjects;
    private final TopicRepository topics;

    private final Map<String, Long> studentIds = new HashMap<>();
    private final Map<String, Long> examIds = new HashMap<>();
    private final Map<String, Long> subjectIds = new HashMap<>();
    private final Map<String, Long> topicIds = new HashMap<>();

    public ResultRowProcessor(StudentRepository students,
                              ExamRepository exams,
                              SubjectRepository subjects,
                              TopicRepository topics) {
        this.students = students;
        this.exams = exams;
        this.subjects = subjects;
        this.topics = topics;
    }

    @Override
    public TopicResultRow process(ResultCsvRow row) {
        requireText(row, row.studentName(), "student_name");
        requireText(row, row.className(), "class_name");
        requireText(row, row.chapterName(), "chapter_name");

        Long studentId = resolveStudentId(row);
        Long examId = resolveExamId(row);
        Long subjectId = resolveSubjectId(row);
        Long topicId = resolveTopicId(row, subjectId);

        BigDecimal marks = requireDecimal(row, row.marksObtained(), "marks_obtained");
        BigDecimal maxMarks = requireDecimal(row, row.maximumMarks(), "maximum_marks");

        if (maxMarks.signum() <= 0) {
            throw RowValidationException.of(row,
                    "maximum_marks must be greater than zero, found " + maxMarks.toPlainString());
        }
        if (marks.signum() < 0) {
            throw RowValidationException.of(row,
                    "marks_obtained cannot be negative, found " + marks.toPlainString());
        }
        if (marks.compareTo(maxMarks) > 0) {
            throw RowValidationException.of(row, "marks_obtained (%s) exceeds maximum_marks (%s)"
                    .formatted(marks.toPlainString(), maxMarks.toPlainString()));
        }

        return new TopicResultRow(
                row.lineNumber(),
                row.rawLine(),
                studentId,
                examId,
                subjectId,
                topicId,
                row.subject(),
                row.topicName(),
                marks,
                maxMarks);
    }

    private Long resolveStudentId(ResultCsvRow row) {
        requireText(row, row.studentId(), "student_id");
        Long id = studentIds.computeIfAbsent(row.studentId(),
                admissionNo -> students.findByAdmissionNo(admissionNo).map(Student::getId).orElse(null));
        if (id == null) {
            throw RowValidationException.of(row, "Unknown student_id '" + row.studentId() + "'");
        }
        return id;
    }

    private Long resolveExamId(ResultCsvRow row) {
        requireText(row, row.examName(), "exam_name");
        Long id = examIds.computeIfAbsent(row.examName(),
                name -> exams.findByCode(CsvSlug.of(name, EXAM_CODE_LENGTH)).map(Exam::getId).orElse(null));
        if (id == null) {
            throw RowValidationException.of(row, "Unknown exam_name '" + row.examName() + "'");
        }
        return id;
    }

    private Long resolveSubjectId(ResultCsvRow row) {
        requireText(row, row.subject(), "subject");
        Long id = subjectIds.computeIfAbsent(row.subject(),
                name -> subjects.findByCode(CsvSlug.of(name, SUBJECT_CODE_LENGTH))
                        .map(Subject::getId)
                        .orElse(null));
        if (id == null) {
            throw RowValidationException.of(row, "Unknown subject '" + row.subject() + "'");
        }
        return id;
    }

    private Long resolveTopicId(ResultCsvRow row, Long subjectId) {
        requireText(row, row.topicName(), "topic_name");
        String key = subjectId + "::" + row.topicName().toLowerCase();
        Long id = topicIds.computeIfAbsent(key,
                ignored -> topics.findBySubjectIdAndNameIgnoreCase(subjectId, row.topicName())
                        .map(Topic::getId)
                        .orElse(null));
        if (id == null) {
            throw RowValidationException.of(row, "Topic '%s' is not defined for subject '%s'"
                    .formatted(row.topicName(), row.subject()));
        }
        return id;
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
