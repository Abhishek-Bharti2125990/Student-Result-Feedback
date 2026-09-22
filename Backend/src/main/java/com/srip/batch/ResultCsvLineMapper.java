package com.srip.batch;

import org.springframework.batch.item.file.LineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.file.transform.FieldSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a CSV line into a {@link ResultCsvRow}.
 *
 * <p>Implemented as a {@code LineMapper} rather than the usual
 * tokenizer-plus-{@code FieldSetMapper} pair for one reason: {@code LineMapper}
 * receives the line number, and a {@code FieldSetMapper} does not. Without it,
 * an error report could only say that some row was wrong, not which one - the
 * difference between a usable report and a useless one on a file of 900 rows.
 *
 * <p>Fields are read by position from an unnamed tokenizer rather than by
 * column name. A named tokenizer pads absent columns with blanks, so a row
 * truncated to three columns would arrive looking like a complete row with
 * empty marks, and the uploader would be told the marks were blank rather than
 * that the row was short.
 *
 * <p>Expected header:
 * <pre>
 * admission_no,exam_code,subject_code,marks_obtained,max_marks,attempted,remarks,topic_breakdown
 * </pre>
 * The last three columns are optional. {@code topic_breakdown} uses
 * {@code Topic:got/max} entries separated by {@code |}, for example
 * {@code Algebra:18/25|Geometry:20/25}.
 */
public class ResultCsvLineMapper implements LineMapper<ResultCsvRow> {

    static final String[] COLUMNS = {
            "admission_no", "exam_code", "subject_code",
            "marks_obtained", "max_marks", "attempted", "remarks", "topic_breakdown"
    };

    /** Everything up to and including {@code max_marks} must be present. */
    static final int REQUIRED_COLUMNS = 5;

    private static final int IDX_ADMISSION_NO = 0;
    private static final int IDX_EXAM_CODE = 1;
    private static final int IDX_SUBJECT_CODE = 2;
    private static final int IDX_MARKS = 3;
    private static final int IDX_MAX_MARKS = 4;
    private static final int IDX_ATTEMPTED = 5;
    private static final int IDX_REMARKS = 6;
    private static final int IDX_TOPICS = 7;

    private static final String TOPIC_SEPARATOR = "\\|";

    private final DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();

    @Override
    public ResultCsvRow mapLine(String line, int lineNumber) {
        FieldSet fields = tokenizer.tokenize(line);

        if (fields.getFieldCount() < REQUIRED_COLUMNS) {
            throw new RowValidationException(lineNumber, line,
                    "Expected at least %d columns (%s) but found %d"
                            .formatted(REQUIRED_COLUMNS, String.join(", ", requiredColumnNames()),
                                    fields.getFieldCount()));
        }

        return new ResultCsvRow(
                lineNumber,
                line,
                at(fields, IDX_ADMISSION_NO),
                at(fields, IDX_EXAM_CODE),
                at(fields, IDX_SUBJECT_CODE),
                at(fields, IDX_MARKS),
                at(fields, IDX_MAX_MARKS),
                at(fields, IDX_ATTEMPTED),
                at(fields, IDX_REMARKS),
                parseTopics(lineNumber, line, at(fields, IDX_TOPICS)));
    }

    /**
     * @return the topic entries, or an empty list when the column is absent;
     *         subject-level marks alone are a valid upload
     */
    private List<ResultCsvRow.TopicMark> parseTopics(int lineNumber, String line, String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        List<ResultCsvRow.TopicMark> topics = new ArrayList<>();
        for (String entry : raw.split(TOPIC_SEPARATOR)) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // Split on the last colon and last slash so topic names may
            // themselves contain punctuation, e.g. "Physics - Optics".
            int colon = trimmed.lastIndexOf(':');
            int slash = trimmed.lastIndexOf('/');
            if (colon <= 0 || slash <= colon + 1 || slash == trimmed.length() - 1) {
                throw new RowValidationException(lineNumber, line,
                        "Malformed topic_breakdown entry '%s'; expected Topic:got/max".formatted(trimmed));
            }

            topics.add(new ResultCsvRow.TopicMark(
                    trimmed.substring(0, colon).trim(),
                    trimmed.substring(colon + 1, slash).trim(),
                    trimmed.substring(slash + 1).trim()));
        }
        return topics;
    }

    /** @return the trimmed field at {@code index}, or null if absent or blank */
    private static String at(FieldSet fields, int index) {
        if (index >= fields.getFieldCount()) {
            return null;
        }
        String value = fields.readString(index);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> requiredColumnNames() {
        return List.of(COLUMNS).subList(0, REQUIRED_COLUMNS);
    }
}
