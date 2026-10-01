import { Loader2 } from 'lucide-react';
import { cn } from '@/lib/cn';

interface LoaderProps {
    label?: string;
    className?: string;
    size?: 'sm' | 'md' | 'lg';
}

const SIZES = {
    sm: 'size-4',
    md: 'size-6',
    lg: 'size-8',
};

/** Spinner plus a label. Use a skeleton instead where the shape is known. */
export function Loader({ label = 'Loading…', className, size = 'md' }: LoaderProps) {
    return (
        <div
            className={cn('flex items-center justify-center gap-3 py-10 text-slate-500', className)}
            role="status"
            aria-live="polite"
        >
            <Loader2 className={cn('animate-spin text-blue-600', SIZES[size])} aria-hidden />
            <span className="text-sm">{label}</span>
        </div>
    );
}
