/**
 * Joins class names, dropping anything falsy.
 *
 * A three-line helper rather than `clsx`: every component here conditions a few
 * classes at most, and that is not worth a dependency.
 */
export function cn(...parts: Array<string | false | null | undefined>): string {
    return parts.filter(Boolean).join(' ');
}
