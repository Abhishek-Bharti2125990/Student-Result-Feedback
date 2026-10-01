import type { ScoreCategory } from '@/types/common';

/**
 * The visual language for the four score bands, defined once.
 *
 * Colour is the whole point of these bands on a dashboard, so it cannot be
 * decided per component - a CRITICAL student rendered amber on one panel and
 * red on another would quietly undermine the only signal the page carries.
 *
 * The brief's mapping: red, yellow, blue, green.
 */
export interface CategoryStyle {
    /** Short label for a badge. */
    label: string;
    /** The brief's full heading, also returned by the backend. */
    heading: string;
    /** Badge and pill: background, text, border. */
    badge: string;
    /** A solid block, for KPI tiles and bucket tabs. */
    solid: string;
    /** Tinted surface for a card body. */
    surface: string;
    /** Accent bar or left border. */
    accent: string;
    /** Hex for Recharts, which cannot read Tailwind classes. */
    hex: string;
    description: string;
}

export const CATEGORY_ORDER: ScoreCategory[] = ['CRITICAL', 'AVERAGE', 'GOOD', 'EXCELLENT'];

export const CATEGORY_STYLES: Record<ScoreCategory, CategoryStyle> = {
    CRITICAL: {
        label: 'Critical',
        heading: 'Students Below 50%',
        badge: 'bg-red-50 text-red-700 ring-red-200',
        solid: 'bg-red-500 text-white',
        surface: 'bg-red-50/60',
        accent: 'bg-red-500',
        hex: '#ef4444',
        description: 'Needs intervention now',
    },
    AVERAGE: {
        label: 'Average',
        heading: 'Students Between 50 and 70',
        badge: 'bg-amber-50 text-amber-700 ring-amber-200',
        solid: 'bg-amber-500 text-white',
        surface: 'bg-amber-50/60',
        accent: 'bg-amber-500',
        hex: '#f59e0b',
        description: 'Steady, with clear gaps',
    },
    GOOD: {
        label: 'Good',
        heading: 'Students Between 70 and 85',
        badge: 'bg-blue-50 text-blue-700 ring-blue-200',
        solid: 'bg-blue-600 text-white',
        surface: 'bg-blue-50/60',
        accent: 'bg-blue-600',
        hex: '#2563eb',
        description: 'On track',
    },
    EXCELLENT: {
        label: 'Excellent',
        heading: 'Students Above 85',
        badge: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
        solid: 'bg-emerald-600 text-white',
        surface: 'bg-emerald-50/60',
        accent: 'bg-emerald-600',
        hex: '#059669',
        description: 'Ready to be stretched',
    },
};

export function categoryStyle(category: ScoreCategory | undefined): CategoryStyle {
    return CATEGORY_STYLES[category ?? 'CRITICAL'];
}

/**
 * The same banding the backend applies, for values it has not categorised -
 * a single subject's percentage, for instance.
 *
 * Boundaries fall into the higher band, matching `GradingService.category`:
 * 50 is AVERAGE, not CRITICAL.
 */
export function categoryOf(percentage: number): ScoreCategory {
    if (percentage < 50) return 'CRITICAL';
    if (percentage < 70) return 'AVERAGE';
    if (percentage < 85) return 'GOOD';
    return 'EXCELLENT';
}
