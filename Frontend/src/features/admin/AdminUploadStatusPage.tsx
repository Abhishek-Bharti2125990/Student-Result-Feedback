import { Link, useParams } from 'react-router-dom';
import { Check, History, UploadCloud, X } from 'lucide-react';
import { PageHeader } from '@/components/layout/AppLayout';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { CardSkeleton } from '@/components/ui/Skeleton';
import { cn } from '@/lib/cn';
import type { UploadStatus } from '@/types/admin';
import { UploadErrorReport } from './UploadErrorReport';
import { UploadStatusPanel } from './UploadStatusPanel';
import { useUploadJobPolling } from './useUploadJobPolling';

/**
 * Processing status for one import, at `/admin/uploads/:jobId`.
 *
 * A page of its own rather than only a panel on the upload screen, so an
 * import can be watched - or linked to - after the admin has navigated away,
 * and so a past job from the history list opens the same view as a live one.
 */
export function AdminUploadStatusPage() {
    const { jobId: rawId } = useParams();
    const jobId = rawId && /^\d+$/.test(rawId) ? Number(rawId) : null;
    const { job, error, retry } = useUploadJobPolling(jobId);

    const header = (
        <PageHeader
            title={jobId !== null ? `Processing status · Job ${jobId}` : 'Processing status'}
            description="Live progress of one batch import, with its row-level error report"
            action={
                <div className="flex gap-2">
                    <Link to="/admin/uploads">
                        <Button variant="outline" size="sm" leftIcon={<History className="size-3.5" aria-hidden />}>
                            All uploads
                        </Button>
                    </Link>
                    <Link to="/admin/upload">
                        <Button size="sm" leftIcon={<UploadCloud className="size-3.5" aria-hidden />}>
                            New upload
                        </Button>
                    </Link>
                </div>
            }
        />
    );

    if (jobId === null) {
        return (
            <>
                {header}
                <Card>
                    <EmptyState
                        title="Not a valid job id"
                        description="Open an import from the upload history to see its status."
                    />
                </Card>
            </>
        );
    }

    if (!job && error) {
        return (
            <>
                {header}
                <ErrorState message={error} onRetry={retry} />
            </>
        );
    }

    if (!job) {
        return (
            <>
                {header}
                <CardSkeleton lines={5} />
            </>
        );
    }

    return (
        <>
            {header}
            <div className="space-y-6">
                <Card>
                    <StatusStepper status={job.status} />
                </Card>

                <UploadStatusPanel job={job} />

                {error && (
                    <ErrorState title="Live updates paused" message={error} onRetry={retry} />
                )}

                {job.errors.length > 0 ? (
                    <UploadErrorReport errors={job.errors} />
                ) : (
                    job.status !== 'PENDING' &&
                    job.status !== 'RUNNING' &&
                    job.status !== 'FAILED' && (
                        <Card>
                            <EmptyState
                                title="No rejected rows"
                                description="Every row in this file was imported."
                            />
                        </Card>
                    )
                )}
            </div>
        </>
    );
}

type StepState = 'done' | 'current' | 'failed' | 'todo';

/** QUEUED -> PROCESSING -> COMPLETED, with the last step turning red on FAILED. */
function StatusStepper({ status }: { status: UploadStatus }) {
    const finished = status === 'COMPLETED' || status === 'COMPLETED_WITH_ERRORS';

    const steps: Array<{ label: string; state: StepState }> = [
        { label: 'Queued', state: status === 'PENDING' ? 'current' : 'done' },
        {
            label: 'Processing',
            state: status === 'PENDING' ? 'todo' : status === 'RUNNING' ? 'current' : 'done',
        },
        {
            label:
                status === 'FAILED'
                    ? 'Failed'
                    : status === 'COMPLETED_WITH_ERRORS'
                      ? 'Completed with errors'
                      : 'Completed',
            state: status === 'FAILED' ? 'failed' : finished ? 'done' : 'todo',
        },
    ];

    return (
        <ol className="flex items-center gap-2 sm:gap-3">
            {steps.map((step, index) => (
                <li key={step.label} className="flex min-w-0 flex-1 items-center gap-2 sm:gap-3">
                    <span
                        className={cn(
                            'grid size-8 shrink-0 place-items-center rounded-full text-xs font-semibold',
                            step.state === 'done' && 'bg-emerald-600 text-white',
                            step.state === 'current' && 'bg-blue-600 text-white ring-4 ring-blue-100',
                            step.state === 'failed' && 'bg-red-600 text-white',
                            step.state === 'todo' && 'bg-slate-100 text-slate-400',
                        )}
                        aria-hidden
                    >
                        {step.state === 'done' ? (
                            <Check className="size-4" />
                        ) : step.state === 'failed' ? (
                            <X className="size-4" />
                        ) : (
                            index + 1
                        )}
                    </span>
                    <span
                        className={cn(
                            'truncate text-sm font-medium',
                            step.state === 'todo' ? 'text-slate-400' : 'text-slate-900',
                        )}
                    >
                        {step.label}
                    </span>
                    {index < steps.length - 1 && (
                        <span
                            className={cn(
                                'hidden h-0.5 flex-1 rounded-full sm:block',
                                step.state === 'done' ? 'bg-emerald-200' : 'bg-slate-200',
                            )}
                            aria-hidden
                        />
                    )}
                </li>
            ))}
        </ol>
    );
}
