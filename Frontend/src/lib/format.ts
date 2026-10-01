/** Display helpers. Everything here tolerates a missing value. */

/** `79.5` -> `"79.5%"`. Trailing zeros are dropped: "79.50%" reads as noise. */
export function percent(value: number | null | undefined, fractionDigits = 1): string {
    if (value === null || value === undefined || Number.isNaN(value)) return '—';
    const rounded = Number(value.toFixed(fractionDigits));
    return `${rounded}%`;
}

/** A signed delta, so "+4.2" and "-4.2" are distinguishable at a glance. */
export function signedPercent(value: number | null | undefined): string {
    if (value === null || value === undefined || Number.isNaN(value)) return '—';
    const rounded = Number(value.toFixed(1));
    return `${rounded > 0 ? '+' : ''}${rounded}%`;
}

export function marks(obtained: number | null | undefined, max: number | null | undefined): string {
    if (obtained === null || obtained === undefined || max === null || max === undefined) return '—';
    return `${trimNumber(obtained)} / ${trimNumber(max)}`;
}

/** `18.00` -> `"18"`, `18.50` -> `"18.5"`. */
export function trimNumber(value: number): string {
    return String(Number(value.toFixed(2)));
}

/** `3` of `8` -> `"3 of 8"`; rank 0 means "did not sit this exam". */
export function rank(position: number, total: number): string {
    if (!position) return '—';
    return `${position} of ${total}`;
}

/** An ISO instant as a short local date and time. */
export function dateTime(iso: string | null | undefined): string {
    if (!iso) return '—';
    const parsed = new Date(iso);
    if (Number.isNaN(parsed.getTime())) return '—';
    return parsed.toLocaleString(undefined, {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    });
}

export function dateOnly(iso: string | null | undefined): string {
    if (!iso) return '—';
    const parsed = new Date(iso);
    if (Number.isNaN(parsed.getTime())) return '—';
    return parsed.toLocaleDateString(undefined, {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
    });
}

export function fileSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** Pluralises a count: `1 student`, `25 students`. */
export function plural(count: number, singular: string, pluralForm?: string): string {
    const word = count === 1 ? singular : (pluralForm ?? `${singular}s`);
    return `${count} ${word}`;
}
