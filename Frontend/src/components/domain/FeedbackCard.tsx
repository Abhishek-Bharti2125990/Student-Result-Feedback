import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

interface FeedbackCardProps {
    title: string;
    icon: ReactNode;
    items: string[];
    tone: 'strength' | 'weakness' | 'neutral';
    emptyText?: string;
}

const TONES = {
    strength: {
        chip: 'bg-emerald-100 text-emerald-700',
        marker: 'bg-emerald-500',
        ring: 'ring-emerald-100',
        surface: 'bg-emerald-50/40',
    },
    weakness: {
        chip: 'bg-amber-100 text-amber-700',
        marker: 'bg-amber-500',
        ring: 'ring-amber-100',
        surface: 'bg-amber-50/40',
    },
    neutral: {
        chip: 'bg-blue-100 text-blue-700',
        marker: 'bg-blue-500',
        ring: 'ring-blue-100',
        surface: 'bg-blue-50/40',
    },
} as const;

/** A titled list of AI-written lines - strengths, weaknesses, suggestions. */
export function FeedbackCard({ title, icon, items, tone, emptyText }: FeedbackCardProps) {
    const styles = TONES[tone];

    return (
        <div className={cn('rounded-xl p-4 ring-1', styles.surface, styles.ring)}>
            <div className="flex items-center gap-2">
                <span className={cn('grid size-7 place-items-center rounded-lg', styles.chip)} aria-hidden>
                    {icon}
                </span>
                <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
                <span className="ml-auto text-xs font-medium tabular-nums text-slate-500">
                    {items.length}
                </span>
            </div>

            {items.length === 0 ? (
                <p className="mt-3 text-sm text-slate-500">{emptyText ?? 'Nothing recorded.'}</p>
            ) : (
                <ul className="mt-3 space-y-2.5">
                    {items.map((item, index) => (
                        <li key={index} className="flex gap-2.5 text-sm leading-relaxed text-slate-700">
                            <span
                                className={cn('mt-[0.45rem] size-1.5 shrink-0 rounded-full', styles.marker)}
                                aria-hidden
                            />
                            <span>{item}</span>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}
