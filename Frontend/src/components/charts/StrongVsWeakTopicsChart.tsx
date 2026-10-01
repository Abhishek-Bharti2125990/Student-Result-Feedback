import {
    Bar,
    BarChart,
    CartesianGrid,
    Cell,
    ReferenceLine,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts';
import { AXIS_PROPS, CHART_COLORS, TOOLTIP_STYLE, percentTick } from './chartTheme';
import type { TopicHighlight } from '@/types/common';

interface Props {
    strongTopics: TopicHighlight[];
    weakTopics: TopicHighlight[];
    /** How many of each to plot, worst and best first. */
    limit?: number;
}

/**
 * Strengths and weaknesses on one axis, sorted into a single ramp.
 *
 * Two separate charts would let a reader miss the thing that matters - the
 * distance between the two ends. Putting them on one scale, with the 50% line
 * drawn, makes "these four are costing you marks and these four are not" a
 * single glance rather than a comparison.
 */
export function StrongVsWeakTopicsChart({ strongTopics, weakTopics, limit = 5 }: Props) {
    const data = [
        ...weakTopics.slice(0, limit).map((topic) => ({
            name: topic.topic,
            subject: topic.subject ?? '',
            percentage: Number(topic.percentage.toFixed(1)),
            fill: CHART_COLORS.weak,
            kind: 'Weak',
        })),
        ...strongTopics.slice(0, limit).map((topic) => ({
            name: topic.topic,
            subject: topic.subject ?? '',
            percentage: Number(topic.percentage.toFixed(1)),
            fill: CHART_COLORS.strong,
            kind: 'Strong',
        })),
    ].sort((a, b) => a.percentage - b.percentage);

    return (
        <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} layout="vertical" margin={{ top: 4, right: 16, left: 8, bottom: 4 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={CHART_COLORS.grid} horizontal={false} />
                <XAxis type="number" domain={[0, 100]} tickFormatter={percentTick} {...AXIS_PROPS} />
                <YAxis
                    type="category"
                    dataKey="name"
                    width={132}
                    {...AXIS_PROPS}
                    tick={{ fontSize: 11, fill: CHART_COLORS.axis }}
                />
                <Tooltip
                    {...TOOLTIP_STYLE}
                    formatter={(value, _name, item) => [`${value}%`, item.payload.kind]}
                    labelFormatter={(label, payload) => {
                        const subject = payload?.[0]?.payload?.subject;
                        return subject ? `${label} · ${subject}` : String(label);
                    }}
                />
                <ReferenceLine x={50} stroke={CHART_COLORS.axis} strokeDasharray="4 4" />
                <Bar dataKey="percentage" radius={[0, 4, 4, 0]} maxBarSize={20}>
                    {data.map((entry, index) => (
                        <Cell key={`${entry.name}-${index}`} fill={entry.fill} />
                    ))}
                </Bar>
            </BarChart>
        </ResponsiveContainer>
    );
}
