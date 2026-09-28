package com.srip.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CsvSlugTest {

    @ParameterizedTest
    @CsvSource({
            "Mathematics,      MATHEMATICS",
            "Social Science,   SOCIAL-SCIENCE",
            "Computer Science, COMPUTER-SCIENCE",
            "Unit Test 1,      UNIT-TEST-1",
            "Midterm,          MIDTERM"
    })
    void producesTheCodesSeededByMigrationV2(String name, String expected) {
        // These must match the seeded rows exactly. A mismatch would not fail
        // loudly - the importer would quietly create a second subject.
        assertThat(CsvSlug.of(name, 64)).isEqualTo(expected);
    }

    @Test
    void differencesInCaseAndPaddingResolveToTheSameCode() {
        // The same name typed twice in a spreadsheet must not become two rows.
        assertThat(CsvSlug.of("  mathematics ", 32)).isEqualTo(CsvSlug.of("Mathematics", 32));
    }

    @Test
    void collapsesRunsOfPunctuationIntoASingleSeparator() {
        assertThat(CsvSlug.of("Physics -- Optics & Light", 64)).isEqualTo("PHYSICS-OPTICS-LIGHT");
    }

    @Test
    void neverEndsInASeparatorWhenTruncatedToTheColumnWidth() {
        // A trailing hyphen would be invisible in the database and confusing in
        // an error message.
        assertThat(CsvSlug.of("Applied Mathematics", 8)).isEqualTo("APPLIED");
    }

    @Test
    void truncatesToTheColumnWidth() {
        assertThat(CsvSlug.of("Environmental Studies", 13)).hasSizeLessThanOrEqualTo(13);
    }

    @Test
    void blankAndPunctuationOnlyNamesHaveNoCode() {
        // The caller treats null as "this row cannot name a subject", which the
        // row validator then reports against the offending column.
        assertThat(CsvSlug.of(null, 32)).isNull();
        assertThat(CsvSlug.of("   ", 32)).isNull();
        assertThat(CsvSlug.of("---", 32)).isNull();
    }
}
