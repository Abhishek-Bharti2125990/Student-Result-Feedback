import {
    Bar,
    BarChart,
    CartesianGrid,
    LabelList,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts';
import { plural } from '@/lib/format';
import { AXIS_PROPS, CHART_COLORS, TOOLTIP_STYLE } from './chartTheme';
import type { ClassTopicWeakness } from '@/types/common';

interface Props {
    topics: ClassTopicWeakness[];
    /** Cap the bars; a chart of thirty topics is not a re-teaching plan. */
    limit?: number;
}

/**
 * How many students are weak on each topic - the re-teach queue.
 *
 * Horizontal, because topic names are long: rotated vertical labels are
 * noticeably slower to read, and these labels are the content.
 *
 * Ordered by student count, not by class average. The count is what turns a
 * private gap into a lesson: a topic 25 students failed needs re-teaching, one
 * two students failed needs two conversations.
 */
export function WeakTopicChart({ topics, limit = 8 }: Props) {
    const data = topics.slice(0, limit).map((topic) => ({
        name: topic.topicName,
        subject: topic.subjectName,
        weakStudents: topic.weakStudents,
        average: Number(topic.classAveragePercentage.toFixed(1)),
    }));

    return (
        <ResponsiveContainer width="100%" height="100%">
            <BarChart
                data={data}
                layout="vertical"
                margin={{ top: 4, right: 32, left: 8, bottom: 4 }}
            >
                <CartesianGrid strokeDasharray="3 3" stroke={CHART_COLORS.grid} horizontal={false} />
                <XAxis type="number" allowDecimals={false} {...AXIS_PROPS} />
                <YAxis
                    type="category"
                    dataKey="name"
                    width={132}
                    {...AXIS_PROPS}
                    tick={{ fontSize: 11, fill: CHART_COLORS.axis }}
                />
                <Tooltip
                    {...TOOLTIP_STYLE}
                    formatter={(value, _name, item) => [
                        `${plural(Number(value), 'student')} weak · class average ${item.payload.average}%`,
                        item.payload.subject,
                    ]}
                />
                <Bar dataKey="weakStudents" fill={CHART_COLORS.weak} radius={[0, 4, 4, 0]} maxBarSize={22}>
                    <LabelList
                        dataKey="weakStudents"
                        position="right"
                        style={{ fontSize: 11, fill: CHART_COLORS.axis, fontWeight: 600 }}
                    />
                </Bar>
            </BarChart>
        </ResponsiveContainer>
    );
}
