import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { CATEGORY_STYLES } from '@/lib/category';
import { plural } from '@/lib/format';
import { TOOLTIP_STYLE } from './chartTheme';
import type { CategoryCount } from '@/types/common';

/**
 * How the class splits across the four score bands.
 *
 * A donut rather than a bar chart: there are exactly four parts of one whole
 * and the question being asked is "what proportion of my class is in trouble?",
 * which a part-to-whole shape answers directly.
 *
 * Empty bands are dropped. A zero-width slice is invisible but still claims a
 * legend entry and a tooltip target, which is worse than leaving it out - the
 * counts beside the chart already state every band, including the empty ones.
 */
export function CategoryDistributionChart({ counts }: { counts: CategoryCount[] }) {
    const data = counts
        .filter((count) => count.students > 0)
        .map((count) => ({
            name: CATEGORY_STYLES[count.category].label,
            value: count.students,
            fill: CATEGORY_STYLES[count.category].hex,
        }));

    return (
        <ResponsiveContainer width="100%" height="100%">
            <PieChart>
                <Pie
                    data={data}
                    dataKey="value"
                    nameKey="name"
                    innerRadius="52%"
                    outerRadius="80%"
                    paddingAngle={2}
                    strokeWidth={0}
                >
                    {data.map((entry) => (
                        <Cell key={entry.name} fill={entry.fill} />
                    ))}
                </Pie>
                <Tooltip
                    {...TOOLTIP_STYLE}
                    formatter={(value) => plural(Number(value), 'student')}
                />
                {/* `itemSorter={null}` keeps the data order. Recharts otherwise
                    sorts the legend alphabetically - which puts "Average" before
                    "Critical" and loses the worst-to-best reading that the
                    bands are for. */}
                <Legend iconType="circle" wrapperStyle={{ fontSize: '0.75rem' }} itemSorter={null} />
            </PieChart>
        </ResponsiveContainer>
    );
}
