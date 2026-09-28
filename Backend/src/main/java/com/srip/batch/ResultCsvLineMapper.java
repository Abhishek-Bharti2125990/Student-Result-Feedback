package com.srip.batch;

import org.springframework.batch.item.file.LineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.file.transform.FieldSet;

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
 * student_id,student_name,class_name,section,exam_name,subject,chapter_name,topic_name,marks_obtained,maximum_marks
 * </pre>
 * Every column must be present. {@code section} is the only one allowed to be
 * empty, for a class that is not divided into sections.
 */
public class ResultCsvLineMapper implements LineMapper<ResultCsvRow> {

    static final String[] COLUMNS = {
            "student_id", "student_name", "class_name", "section", "exam_name",
            "subject", "chapter_name", "topic_name", "marks_obtained", "maximum_marks"
    };

    static final int COLUMN_COUNT = COLUMNS.length;

    private static final int IDX_STUDENT_ID = 0;
    private static final int IDX_STUDENT_NAME = 1;
    private static final int IDX_CLASS_NAME = 2;
    private static final int IDX_SECTION = 3;
    private static final int IDX_EXAM_NAME = 4;
    private static final int IDX_SUBJECT = 5;
    private static final int IDX_CHAPTER_NAME = 6;
    private static final int IDX_TOPIC_NAME = 7;
    private static final int IDX_MARKS = 8;
    private static final int IDX_MAX_MARKS = 9;

    private final DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();

    @Override
    public ResultCsvRow mapLine(String line, int lineNumber) {
        FieldSet fields = tokenizer.tokenize(line);

        if (fields.getFieldCount() < COLUMN_COUNT) {
            throw new RowValidationException(lineNumber, line,
                    "Expected %d columns (%s) but found %d"
                            .formatted(COLUMN_COUNT, String.join(", ", List.of(COLUMNS)),
                                    fields.getFieldCount()));
        }

        return new ResultCsvRow(
                lineNumber,
                line,
                at(fields, IDX_STUDENT_ID),
                at(fields, IDX_STUDENT_NAME),
                at(fields, IDX_CLASS_NAME),
                at(fields, IDX_SECTION),
                at(fields, IDX_EXAM_NAME),
                at(fields, IDX_SUBJECT),
                at(fields, IDX_CHAPTER_NAME),
                at(fields, IDX_TOPIC_NAME),
                at(fields, IDX_MARKS),
                at(fields, IDX_MAX_MARKS));
    }

    /** @return the trimmed field at {@code index}, or null if absent or blank */
    private static String at(FieldSet fields, int index) {
        if (index >= fields.getFieldCount()) {
            return null;
        }
        String value = fields.readString(index);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
