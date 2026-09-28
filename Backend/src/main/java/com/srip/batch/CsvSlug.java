package com.srip.batch;

import java.util.Locale;

/**
 * Turns a name from the CSV into a stable code.
 *
 * <p>The upload names its subjects and exams ("Mathematics", "Midterm") rather
 * than coding them, but {@code subjects} and {@code exams} are keyed by a unique
 * code. Slugging the name is what makes "Mathematics" on line 2 and
 * " mathematics " on line 900 resolve to the same row instead of creating two.
 *
 * <p>Deliberately lossy and deliberately stable: the same input always gives the
 * same output, and the seeded reference data in migration V2 uses these exact
 * codes so a seeded subject is found rather than duplicated.
 */
public final class CsvSlug {

    private CsvSlug() {
    }

    /**
     * @param name      the display name from the file
     * @param maxLength the width of the target code column; longer slugs are cut
     * @return an uppercase, hyphen-separated code, or null when {@code name} is blank
     */
    public static String of(String name, int maxLength) {
        if (name == null || name.isBlank()) {
            return null;
        }

        String slug = name.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        if (slug.isEmpty()) {
            return null;
        }
        // Trim to the column width, then strip a hyphen the cut may have left
        // dangling so the code never ends in a separator.
        if (slug.length() > maxLength) {
            slug = slug.substring(0, maxLength).replaceAll("-+$", "");
        }
        return slug;
    }
}
