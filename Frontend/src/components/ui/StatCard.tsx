import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

interface StatCardProps {
    label: string;
    value: ReactNode;
    /** One line of context under the value - a rank, a delta, a denominator. */
    hint?: ReactNode;
    icon?: ReactNode;
    /** Tailwind classes for the icon chip, e.g. a category's solid colour. */
    iconClassName?: string;
    /** Accent stripe down the left edge, used by the category tiles. */
    accentClassName?: string;
    className?: string;
    onClick?: () => void;
    selected?: boolean;
}

/** A single KPI tile: label, big number, one line of context. */
export function StatCard({
    label,
    value,
    hint,
    icon,
    iconClassName,
    accentClassName,
    className,
    onClick,
    selected = false,
}: StatCardProps) {
    const interactive = typeof onClick === 'function';

    // A clickable tile has to be a real button, or it is unreachable by
    // keyboard - which on the teacher dashboard would make the category filter
    // mouse-only.
    const Tag = interactive ? 'button' : 'div';

    return (
        <Tag
            type={interactive ? 'button' : undefined}
            onClick={onClick}
            className={cn(
                'relative overflow-hidden rounded-xl bg-white p-5 text-left ring-1 shadow-sm transition',
                selected ? 'ring-2 ring-blue-500' : 'ring-slate-200/80',
                interactive && 'hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600',
                className,
            )}
            aria-pressed={interactive ? selected : undefined}
        >
            {accentClassName && (
                <span className={cn('absolute inset-y-0 left-0 w-1', accentClassName)} aria-hidden />
            )}
            <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                    <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
                    <p className="mt-2 text-2xl font-semibold tabular-nums text-slate-900">{value}</p>
                    {hint && <p className="mt-1.5 text-xs text-slate-500">{hint}</p>}
                </div>
                {icon && (
                    <span
                        className={cn(
                            'grid size-10 shrink-0 place-items-center rounded-lg',
                            // Replaced, not layered. Tailwind resolves a conflict
                            // by CSS source order, not by class-string order, so
                            // appending `bg-amber-500` after a default
                            // `bg-blue-50` silently loses - which is how one tile
                            // ends up the wrong colour while its neighbours look
                            // fine.
                            iconClassName ?? 'bg-blue-50 text-blue-600',
                        )}
                    >
                        {icon}
                    </span>
                )}
            </div>
        </Tag>
    );
}
