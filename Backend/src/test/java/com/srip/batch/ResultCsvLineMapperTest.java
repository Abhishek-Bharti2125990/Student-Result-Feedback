package com.srip.batch;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultCsvLineMapperTest {

    private final ResultCsvLineMapper mapper = new ResultCsvLineMapper();

    @Test
    void parsesAFullRowIncludingTheTopicBreakdown() {
        ResultCsvRow row = mapper.mapLine(
                "STU1001,UT1-2025,MATH,78,100,true,solid,Algebra:12/25|Geometry:22/25", 2);

        assertThat(row.lineNumber()).isEqualTo(2);
        assertThat(row.admissionNo()).isEqualTo("STU1001");
        assertThat(row.examCode()).isEqualTo("UT1-2025");
        assertThat(row.subjectCode()).isEqualTo("MATH");
        assertThat(row.marksObtained()).isEqualTo("78");
        assertThat(row.maxMarks()).isEqualTo("100");
        assertThat(row.attempted()).isEqualTo("true");
        assertThat(row.remarks()).isEqualTo("solid");
        assertThat(row.topicMarks()).hasSize(2);
        assertThat(row.topicMarks().get(0).topicName()).isEqualTo("Algebra");
        assertThat(row.topicMarks().get(0).marksObtained()).isEqualTo("12");
        assertThat(row.topicMarks().get(0).maxMarks()).isEqualTo("25");
    }

    @Test
    void acceptsARowWithOnlyTheRequiredColumns() {
        ResultCsvRow row = mapper.mapLine("STU1001,UT1-2025,MATH,78,100", 2);

        assertThat(row.attempted()).isNull();
        assertThat(row.remarks()).isNull();
        assertThat(row.topicMarks()).isEmpty();
    }

    @Test
    void treatsAnEmptyTopicColumnAsNoBreakdown() {
        // Subject-level marks alone are a legitimate upload.
        ResultCsvRow row = mapper.mapLine("STU1001,UT1-2025,ENG,88,100,true,,", 2);

        assertThat(row.topicMarks()).isEmpty();
    }

    @Test
    void keepsTopicNamesContainingSpacesAndHyphens() {
        ResultCsvRow row = mapper.mapLine(
                "STU1001,UT1-2025,SCI,82,100,true,,Physics - Motion:20/25|Biology - Life Processes:21/25", 2);

        assertThat(row.topicMarks()).extracting(ResultCsvRow.TopicMark::topicName)
                .containsExactly("Physics - Motion", "Biology - Life Processes");
        assertThat(row.topicMarks().get(0).marksObtained()).isEqualTo("20");
    }

    @Test
    void reportsTheLineNumberWhenThereAreTooFewColumns() {
        assertThatThrownBy(() -> mapper.mapLine("STU1001,UT1-2025,MATH", 7))
                .isInstanceOf(RowValidationException.class)
                .hasMessageContaining("at least 5 columns")
                .extracting(failure -> ((RowValidationException) failure).getLineNumber())
                .isEqualTo(7);
    }

    @Test
    void rejectsAMalformedTopicEntryWithTheOffendingText() {
        assertThatThrownBy(() -> mapper.mapLine(
                "STU1001,UT1-2025,MATH,78,100,true,,Algebra 12 of 25", 3))
                .isInstanceOf(RowValidationException.class)
                .hasMessageContaining("Algebra 12 of 25")
                .hasMessageContaining("Topic:got/max");
    }

    @Test
    void keepsTheRawLineSoAnErrorReportCanQuoteIt() {
        String line = "STU1001,UT1-2025,MATH,78,100";

        assertThat(mapper.mapLine(line, 4).rawLine()).isEqualTo(line);
    }

    @Test
    void trimsSurroundingWhitespaceFromEveryField() {
        ResultCsvRow row = mapper.mapLine("  STU1001 , UT1-2025 , MATH , 78 , 100 ", 2);

        assertThat(row.admissionNo()).isEqualTo("STU1001");
        assertThat(row.marksObtained()).isEqualTo("78");
    }
}
