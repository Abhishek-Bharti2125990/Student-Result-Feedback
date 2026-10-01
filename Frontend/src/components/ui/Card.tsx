import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '@/lib/cn';

interface CardProps extends HTMLAttributes<HTMLDivElement> {
    children: ReactNode;
    /** Removes the inner padding, for a card whose child owns its own spacing. */
    flush?: boolean;
}

/** The white surface everything in this app sits on: soft shadow, hairline ring. */
export function Card({ className, children, flush = false, ...rest }: CardProps) {
    return (
        <div
            className={cn(
                'rounded-xl bg-white ring-1 ring-slate-200/80 shadow-sm',
                !flush && 'p-5',
                className,
            )}
            {...rest}
        >
            {children}
        </div>
    );
}

interface CardHeaderProps {
    title: ReactNode;
    subtitle?: ReactNode;
    icon?: ReactNode;
    /** Buttons or a filter, right-aligned on the same baseline as the title. */
    action?: ReactNode;
    className?: string;
}

export function CardHeader({ title, subtitle, icon, action, className }: CardHeaderProps) {
    return (
        <div className={cn('flex items-start justify-between gap-3', className)}>
            <div className="flex items-start gap-3 min-w-0">
                {icon && (
                    <span className="mt-0.5 grid size-9 shrink-0 place-items-center rounded-lg bg-blue-50 text-blue-600">
                        {icon}
                    </span>
                )}
                <div className="min-w-0">
                    <h2 className="text-base font-semibold text-slate-900 truncate">{title}</h2>
                    {subtitle && <p className="mt-0.5 text-sm text-slate-500">{subtitle}</p>}
                </div>
            </div>
            {action && <div className="shrink-0">{action}</div>}
        </div>
    );
}

export function CardSection({ className, children }: { className?: string; children: ReactNode }) {
    return <div className={cn('mt-4', className)}>{children}</div>;
}
