/**
 * Shared chart tokens.
 *
 * Recharts takes hex values, not Tailwind classes, so the palette has to be
 * duplicated here. Keeping it in one module means a chart and the cards beside
 * it cannot drift to different blues.
 */

export const CHART_COLORS = {
    /** The student's own score - the primary series throughout. */
    primary: '#2563eb',
    /** The class average, a muted comparison that must not compete. */
    comparison: '#94a3b8',
    strong: '#059669',
    weak: '#ef4444',
    warning: '#f59e0b',
    grid: '#e2e8f0',
    axis: '#64748b',
} as const;

export const AXIS_PROPS = {
    stroke: CHART_COLORS.axis,
    fontSize: 12,
    tickLine: false,
} as const;

/**
 * Tooltip styling applied to every chart.
 *
 * Recharts' default tooltip is a bare bordered box; this matches the cards
 * around it so a hover does not look like a rendering glitch.
 */
export const TOOLTIP_STYLE = {
    contentStyle: {
        borderRadius: '0.5rem',
        border: '1px solid #e2e8f0',
        boxShadow: '0 4px 12px rgb(15 23 42 / 0.08)',
        fontSize: '0.8125rem',
    },
    labelStyle: { fontWeight: 600, color: '#0f172a' },
} as const;

/** Percent formatter for tooltips and axis ticks. */
export function percentTick(value: number): string {
    return `${value}%`;
}

/**
 * Truncates a long label so an axis stays readable.
 *
 * "Chemistry - Chemical Reactions and Equations" would otherwise either be
 * clipped mid-word or force the plot area down to nothing.
 */
export function shortLabel(value: string, max = 14): string {
    if (value.length <= max) return value;
    return `${value.slice(0, max - 1)}…`;
}
