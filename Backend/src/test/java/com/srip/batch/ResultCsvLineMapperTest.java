package com.srip.batch;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultCsvLineMapperTest {

    private final ResultCsvLineMapper mapper = new ResultCsvLineMapper();

    @Test
    void parsesTheDocumentedRowFormat() {
        ResultCsvRow row = mapper.mapLine(
                "1001,Ayushman,10,A,Midterm,Mathematics,Algebra,Quadratic Equations,10,25", 2);

        assertThat(row.lineNumber()).isEqualTo(2);
        assertThat(row.studentId()).isEqualTo("1001");
        assertThat(row.studentName()).isEqualTo("Ayushman");
        assertThat(row.className()).isEqualTo("10");
        assertThat(row.section()).isEqualTo("A");
        assertThat(row.examName()).isEqualTo("Midterm");
        assertThat(row.subject()).isEqualTo("Mathematics");
        assertThat(row.chapterName()).isEqualTo("Algebra");
        assertThat(row.topicName()).isEqualTo("Quadratic Equations");
        assertThat(row.marksObtained()).isEqualTo("10");
        assertThat(row.maximumMarks()).isEqualTo("25");
    }

    @Test
    void anEmptySectionIsAllowedForAClassWithoutSections() {
        ResultCsvRow row = mapper.mapLine(
                "1001,Ayushman,10,,Midterm,Mathematics,Algebra,Polynomials,19,25", 2);

        assertThat(row.section()).isNull();
        assertThat(row.className()).isEqualTo("10");
    }

    @Test
    void keepsNamesContainingSpacesAndHyphens() {
        ResultCsvRow row = mapper.mapLine(
                "1002,Sara Fernandes,10,B,Unit Test 1,Social Science,History,Nationalism,18,25", 3);

        assertThat(row.studentName()).isEqualTo("Sara Fernandes");
        assertThat(row.subject()).isEqualTo("Social Science");
        assertThat(row.topicName()).isEqualTo("Nationalism");
    }

    @Test
    void reportsTheLineNumberWhenThereAreTooFewColumns() {
        assertThatThrownBy(() -> mapper.mapLine("1001,Ayushman,10,A,Midterm,Mathematics", 7))
                .isInstanceOf(RowValidationException.class)
                .hasMessageContaining("Expected 10 columns")
                .extracting(failure -> ((RowValidationException) failure).getLineNumber())
                .isEqualTo(7);
    }

    @Test
    void namesEveryExpectedColumnWhenTheRowIsShort() {
        // The uploader is fixing a spreadsheet, so the message has to say which
        // columns were expected, not just how many.
        assertThatThrownBy(() -> mapper.mapLine("1001,Ayushman", 4))
                .hasMessageContaining("student_id")
                .hasMessageContaining("chapter_name")
                .hasMessageContaining("maximum_marks");
    }

    @Test
    void keepsTheRawLineSoAnErrorReportCanQuoteIt() {
        String line = "1001,Ayushman,10,A,Midterm,Mathematics,Algebra,Polynomials,19,25";

        assertThat(mapper.mapLine(line, 4).rawLine()).isEqualTo(line);
    }

    @Test
    void trimsSurroundingWhitespaceFromEveryField() {
        ResultCsvRow row = mapper.mapLine(
                "  1001 , Ayushman , 10 , A , Midterm , Mathematics , Algebra , Polynomials , 19 , 25 ", 2);

        assertThat(row.studentId()).isEqualTo("1001");
        assertThat(row.subject()).isEqualTo("Mathematics");
        assertThat(row.marksObtained()).isEqualTo("19");
        assertThat(row.maximumMarks()).isEqualTo("25");
    }

    @Test
    void aBlankRequiredFieldArrivesAsNullRatherThanAnEmptyString() {
        // The processor checks for null to build "X is required" messages; an
        // empty string would slip through as a present-but-meaningless value.
        ResultCsvRow row = mapper.mapLine(
                "1001,,10,A,Midterm,Mathematics,Algebra,Polynomials,19,25", 2);

        assertThat(row.studentName()).isNull();
    }
}
