import { cn } from '@/lib/cn';

interface ProgressBarProps {
    /** 0..1. Clamped, so a stray value cannot overflow the track. */
    value: number;
    className?: string;
    barClassName?: string;
    /** Diagonal stripes in motion, for work whose end is not yet known. */
    indeterminate?: boolean;
    label?: string;
}

export function ProgressBar({
    value,
    className,
    barClassName,
    indeterminate = false,
    label,
}: ProgressBarProps) {
    const clamped = Math.max(0, Math.min(1, Number.isFinite(value) ? value : 0));
    const percentage = Math.round(clamped * 100);

    return (
        <div className={cn('w-full', className)}>
            {label && (
                <div className="mb-1.5 flex items-center justify-between text-xs text-slate-500">
                    <span>{label}</span>
                    {!indeterminate && <span className="font-medium text-slate-700">{percentage}%</span>}
                </div>
            )}
            <div
                className="h-2 w-full overflow-hidden rounded-full bg-slate-200"
                role="progressbar"
                aria-valuenow={indeterminate ? undefined : percentage}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-label={label ?? 'Progress'}
            >
                <div
                    className={cn(
                        'h-full rounded-full transition-[width] duration-300',
                        // Replaced rather than layered: see the note in StatCard.
                        barClassName ?? 'bg-blue-600',
                        indeterminate && 'animate-pulse',
                    )}
                    style={{ width: indeterminate ? '100%' : `${percentage}%` }}
                />
            </div>
        </div>
    );
}
