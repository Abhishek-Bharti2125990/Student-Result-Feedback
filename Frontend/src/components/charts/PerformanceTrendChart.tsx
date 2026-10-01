import {
    CartesianGrid,
    Line,
    LineChart,
    ReferenceLine,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts';
import { AXIS_PROPS, CHART_COLORS, TOOLTIP_STYLE, percentTick, shortLabel } from './chartTheme';
import type { TrendPoint } from '@/types/analytics';

interface Props {
    points: TrendPoint[];
    /** Drawn as a dashed guide, so a dip below the pass mark is unmissable. */
    passMark?: number;
}

/**
 * Overall percentage across exams, in date order.
 *
 * The y-axis is **not** zoomed to the data range. A rise from 60 to 78 looks
 * like a vertical cliff on an auto-scaled axis; pinning it to 0-100 keeps the
 * slope honest, which matters when the chart is the evidence for "improving".
 */
export function PerformanceTrendChart({ points, passMark = 40 }: Props) {
    const data = points.map((point) => ({
        name: shortLabel(point.examName, 12),
        fullName: point.examName,
        percentage: Number(point.percentage.toFixed(1)),
    }));

    return (
        <ResponsiveContainer width="100%" height="100%">
            <LineChart data={data} margin={{ top: 8, right: 12, left: -16, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={CHART_COLORS.grid} vertical={false} />
                <XAxis dataKey="name" {...AXIS_PROPS} />
                <YAxis domain={[0, 100]} tickFormatter={percentTick} {...AXIS_PROPS} />
                <Tooltip
                    {...TOOLTIP_STYLE}
                    formatter={(value) => [`${value}%`, 'Overall']}
                    labelFormatter={(_, payload) => payload?.[0]?.payload?.fullName ?? ''}
                />
                <ReferenceLine
                    y={passMark}
                    stroke={CHART_COLORS.weak}
                    strokeDasharray="4 4"
                    label={{ value: 'Pass', position: 'insideTopRight', fontSize: 11, fill: CHART_COLORS.weak }}
                />
                <Line
                    type="monotone"
                    dataKey="percentage"
                    stroke={CHART_COLORS.primary}
                    strokeWidth={2.5}
                    dot={{ r: 4, fill: CHART_COLORS.primary, strokeWidth: 0 }}
                    activeDot={{ r: 6 }}
                />
            </LineChart>
        </ResponsiveContainer>
    );
}
