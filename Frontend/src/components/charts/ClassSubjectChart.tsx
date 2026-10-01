import {
    Bar,
    BarChart,
    CartesianGrid,
    Cell,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts';
import { CATEGORY_STYLES, categoryOf } from '@/lib/category';
import { AXIS_PROPS, CHART_COLORS, TOOLTIP_STYLE, percentTick, shortLabel } from './chartTheme';
import type { SubjectStat } from '@/types/analytics';

/**
 * Class average per subject, weakest first.
 *
 * Each bar is coloured by the band its average falls into, so the subject that
 * needs a decision is red without the teacher reading a single number. The
 * backend already returns these weakest-first; that order is preserved rather
 * than re-sorted alphabetically.
 */
export function ClassSubjectChart({ stats }: { stats: SubjectStat[] }) {
    const data = stats.map((stat) => ({
        name: shortLabel(stat.subjectName),
        fullName: stat.subjectName,
        average: Number(stat.average.toFixed(1)),
        passCount: stat.passCount,
        failCount: stat.failCount,
        fill: CATEGORY_STYLES[categoryOf(stat.average)].hex,
    }));

    return (
        <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={CHART_COLORS.grid} vertical={false} />
                <XAxis dataKey="name" {...AXIS_PROPS} interval={0} />
                <YAxis domain={[0, 100]} tickFormatter={percentTick} {...AXIS_PROPS} />
                <Tooltip
                    {...TOOLTIP_STYLE}
                    formatter={(value, _name, item) => [
                        `${value}% · ${item.payload.passCount} passed, ${item.payload.failCount} failed`,
                        'Class average',
                    ]}
                    labelFormatter={(_, payload) => payload?.[0]?.payload?.fullName ?? ''}
                />
                <Bar dataKey="average" radius={[4, 4, 0, 0]} maxBarSize={48}>
                    {data.map((entry) => (
                        <Cell key={entry.fullName} fill={entry.fill} />
                    ))}
                </Bar>
            </BarChart>
        </ResponsiveContainer>
    );
}
