import type { CSSProperties } from 'react';
import { cn } from '@/lib/cn';

/** A single shimmering block. Compose these to mirror the real layout. */
export function Skeleton({ className, style }: { className?: string; style?: CSSProperties }) {
    return <div className={cn('animate-pulse rounded-md bg-slate-200/70', className)} style={style} />;
}

/**
 * Placeholder for a stat tile row.
 *
 * Skeletons mirror the shape of what is coming rather than being one grey
 * rectangle, so the page does not visibly jump when the data lands.
 */
export function StatRowSkeleton({ count = 4 }: { count?: number }) {
    return (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {Array.from({ length: count }).map((_, index) => (
                <div key={index} className="rounded-xl bg-white p-5 ring-1 ring-slate-200/80 shadow-sm">
                    <Skeleton className="h-3 w-24" />
                    <Skeleton className="mt-3 h-7 w-20" />
                    <Skeleton className="mt-3 h-3 w-32" />
                </div>
            ))}
        </div>
    );
}

export function CardSkeleton({ className, lines = 4 }: { className?: string; lines?: number }) {
    return (
        <div className={cn('rounded-xl bg-white p-5 ring-1 ring-slate-200/80 shadow-sm', className)}>
            <Skeleton className="h-4 w-40" />
            <div className="mt-4 space-y-2.5">
                {Array.from({ length: lines }).map((_, index) => (
                    <Skeleton key={index} className={cn('h-3', index % 3 === 2 ? 'w-2/3' : 'w-full')} />
                ))}
            </div>
        </div>
    );
}

export function ChartSkeleton({ className }: { className?: string }) {
    return (
        <div className={cn('rounded-xl bg-white p-5 ring-1 ring-slate-200/80 shadow-sm', className)}>
            <Skeleton className="h-4 w-48" />
            <div className="mt-6 flex h-52 items-end gap-3">
                {[55, 80, 40, 95, 65, 75].map((height, index) => (
                    <Skeleton key={index} className="flex-1" style={{ height: `${height}%` }} />
                ))}
            </div>
        </div>
    );
}
