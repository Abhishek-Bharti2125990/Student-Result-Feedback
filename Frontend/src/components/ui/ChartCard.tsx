import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';
import { Card, CardHeader } from './Card';
import { EmptyState } from './EmptyState';

interface ChartCardProps {
    title: string;
    subtitle?: string;
    icon?: ReactNode;
    action?: ReactNode;
    /** Fixed height for the plot area. Recharts needs a sized parent. */
    height?: number;
    /** When false, an empty state is shown instead of an axis with no bars. */
    hasData?: boolean;
    emptyTitle?: string;
    emptyDescription?: string;
    className?: string;
    children: ReactNode;
}

/**
 * A chart in a white card, with the empty case handled once.
 *
 * Recharts renders a perfectly good pair of empty axes when handed `[]`, which
 * reads as a broken chart rather than as "no data yet". Every chart in this app
 * goes through here so that never reaches the screen.
 */
export function ChartCard({
    title,
    subtitle,
    icon,
    action,
    height = 288,
    hasData = true,
    emptyTitle = 'Nothing to chart yet',
    emptyDescription,
    className,
    children,
}: ChartCardProps) {
    return (
        <Card className={cn('flex flex-col', className)}>
            <CardHeader title={title} subtitle={subtitle} icon={icon} action={action} />
            {hasData ? (
                <div className="mt-4 w-full" style={{ height }}>
                    {children}
                </div>
            ) : (
                <EmptyState title={emptyTitle} description={emptyDescription} />
            )}
        </Card>
    );
}
