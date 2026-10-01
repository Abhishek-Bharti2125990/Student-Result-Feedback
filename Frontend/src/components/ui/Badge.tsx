import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

type Tone = 'blue' | 'green' | 'amber' | 'red' | 'slate' | 'violet';

const TONES: Record<Tone, string> = {
    blue: 'bg-blue-50 text-blue-700 ring-blue-200',
    green: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
    amber: 'bg-amber-50 text-amber-700 ring-amber-200',
    red: 'bg-red-50 text-red-700 ring-red-200',
    slate: 'bg-slate-100 text-slate-600 ring-slate-200',
    violet: 'bg-violet-50 text-violet-700 ring-violet-200',
};

interface BadgeProps {
    children: ReactNode;
    tone?: Tone;
    /** Pass a prebuilt class triplet from `CATEGORY_STYLES` instead of a tone. */
    classes?: string;
    icon?: ReactNode;
    className?: string;
}

export function Badge({ children, tone = 'slate', classes, icon, className }: BadgeProps) {
    return (
        <span
            className={cn(
                'inline-flex items-center gap-1 rounded-full px-2.5 py-0.5',
                'text-xs font-semibold ring-1 ring-inset whitespace-nowrap',
                classes ?? TONES[tone],
                className,
            )}
        >
            {icon}
            {children}
        </span>
    );
}
