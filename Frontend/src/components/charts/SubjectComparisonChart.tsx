import {
    Bar,
    BarChart,
    CartesianGrid,
    Legend,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts';
import { AXIS_PROPS, CHART_COLORS, TOOLTIP_STYLE, percentTick, shortLabel } from './chartTheme';
import type { SubjectPerformance } from '@/types/common';

/**
 * Student score against the class average, per subject.
 *
 * The comparison is the point: 64% means little on its own, and a great deal
 * once you can see the class managed 71%. The student's bar is the saturated
 * one so the eye lands on it first.
 */
export function SubjectComparisonChart({ subjects }: { subjects: SubjectPerformance[] }) {
    const data = subjects.map((subject) => ({
        name: shortLabel(subject.subjectName),
        fullName: subject.subjectName,
        score: Number(subject.percentage.toFixed(1)),
        // `null` leaves a gap rather than drawing a zero bar, which would read
        // as "the class scored nothing" instead of "no average available".
        classAverage:
            subject.classAverage === undefined ? null : Number(subject.classAverage.toFixed(1)),
    }));

    return (
        <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} margin={{ top: 8, right: 8, left: -16, bottom: 0 }} barGap={4}>
                <CartesianGrid strokeDasharray="3 3" stroke={CHART_COLORS.grid} vertical={false} />
                <XAxis dataKey="name" {...AXIS_PROPS} interval={0} />
                <YAxis domain={[0, 100]} tickFormatter={percentTick} {...AXIS_PROPS} />
                <Tooltip
                    {...TOOLTIP_STYLE}
                    formatter={(value) => (value === null ? '—' : `${value}%`)}
                    labelFormatter={(_, payload) => payload?.[0]?.payload?.fullName ?? ''}
                />
                {/* `itemSorter={null}` keeps series order: Recharts otherwise sorts
                    the legend alphabetically, which would list "Class average"
                    before "Your score" and bury the series the reader came for. */}
                <Legend
                    iconType="circle"
                    wrapperStyle={{ fontSize: '0.75rem', paddingTop: 8 }}
                    itemSorter={null}
                />
                <Bar
                    dataKey="score"
                    name="Your score"
                    fill={CHART_COLORS.primary}
                    radius={[4, 4, 0, 0]}
                    maxBarSize={36}
                />
                <Bar
                    dataKey="classAverage"
                    name="Class average"
                    fill={CHART_COLORS.comparison}
                    radius={[4, 4, 0, 0]}
                    maxBarSize={36}
                />
            </BarChart>
        </ResponsiveContainer>
    );
}
