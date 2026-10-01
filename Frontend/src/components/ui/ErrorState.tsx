import { AlertTriangle, RotateCcw } from 'lucide-react';
import { cn } from '@/lib/cn';
import { Button } from './Button';

interface ErrorStateProps {
    title?: string;
    /** The backend's own message, which says far more than "request failed". */
    message: string;
    onRetry?: () => void;
    retrying?: boolean;
    className?: string;
}

export function ErrorState({
    title = 'Something went wrong',
    message,
    onRetry,
    retrying = false,
    className,
}: ErrorStateProps) {
    return (
        <div
            className={cn(
                'flex flex-col items-center justify-center rounded-xl bg-red-50/60 px-6 py-10 text-center ring-1 ring-red-100',
                className,
            )}
            role="alert"
        >
            <span className="grid size-12 place-items-center rounded-full bg-red-100 text-red-600">
                <AlertTriangle className="size-6" aria-hidden />
            </span>
            <h3 className="mt-4 text-sm font-semibold text-slate-900">{title}</h3>
            <p className="mt-1 max-w-md text-sm text-slate-600">{message}</p>
            {onRetry && (
                <Button
                    variant="outline"
                    size="sm"
                    className="mt-5"
                    onClick={onRetry}
                    loading={retrying}
                    leftIcon={<RotateCcw className="size-3.5" aria-hidden />}
                >
                    Try again
                </Button>
            )}
        </div>
    );
}
