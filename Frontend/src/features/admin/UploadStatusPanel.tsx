import { CheckCircle2, CircleAlert, Clock, Hash, Loader2, XCircle } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { Card, CardHeader } from '@/components/ui/Card';
import { ProgressBar } from '@/components/ui/ProgressBar';
import { cn } from '@/lib/cn';
import { dateTime } from '@/lib/format';
import { isInFlight, statusLabel, type UploadJobView, type UploadStatus } from '@/types/admin';

const STATUS_STYLES: Record<UploadStatus, { badge: string; icon: typeof Clock; iconClass: string }> = {
    PENDING: { badge: 'bg-slate-100 text-slate-600 ring-slate-200', icon: Clock, iconClass: 'text-slate-500' },
    RUNNING: { badge: 'bg-blue-50 text-blue-700 ring-blue-200', icon: Loader2, iconClass: 'text-blue-600 animate-spin' },
    COMPLETED: { badge: 'bg-emerald-50 text-emerald-700 ring-emerald-200', icon: CheckCircle2, iconClass: 'text-emerald-600' },
    COMPLETED_WITH_ERRORS: { badge: 'bg-amber-50 text-amber-700 ring-amber-200', icon: CircleAlert, iconClass: 'text-amber-600' },
    FAILED: { badge: 'bg-red-50 text-red-700 ring-red-200', icon: XCircle, iconClass: 'text-red-600' },
};

export function UploadStatusBadge({ status }: { status: UploadStatus }) {
    const style = STATUS_STYLES[status];
    const Icon = style.icon;
    return (
        <Badge classes={style.badge} icon={<Icon className={cn('size-3', style.iconClass)} aria-hidden />}>
            {statusLabel(status)}
        </Badge>
    );
}

/**
 * Live view of one import.
 *
 * Record counts are shown for every terminal status, including FAILED: a job
 * that died partway still wrote rows, and hiding the counts would leave an
 * operator unable to tell whether to re-upload the whole file.
 */
export function UploadStatusPanel({ job }: { job: UploadJobView }) {
    const style = STATUS_STYLES[job.status];
    const Icon = style.icon;
    const running = isInFlight(job.status);

    const processed = job.validRecords + job.invalidRecords;
    const fraction = job.totalRecords > 0 ? processed / job.totalRecords : 0;

    return (
        <Card>
            <CardHeader
                title="Import status"
                subtitle={job.filename}
                icon={<Icon className={cn('size-4', style.iconClass)} aria-hidden />}
                action={<UploadStatusBadge status={job.status} />}
            />

            <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-1.5 text-xs text-slate-500">
                <span className="inline-flex items-center gap-1.5">
                    <Hash className="size-3" aria-hidden />
                    Job <span className="font-mono font-semibold text-slate-700">{job.id}</span>
                </span>
                {job.jobExecutionId !== undefined && (
                    <span>
                        Batch execution{' '}
                        <span className="font-mono font-semibold text-slate-700">
                            {job.jobExecutionId}
                        </span>
                    </span>
                )}
                <span>Started {dateTime(job.createdAt)}</span>
                {job.completedAt && <span>Finished {dateTime(job.completedAt)}</span>}
            </div>

            <div className="mt-4">
                <ProgressBar
                    value={running ? 0 : fraction}
                    indeterminate={running}
                    label={
                        running
                            ? 'Reading, validating, ranking and writing feedback…'
                            : `${processed} of ${job.totalRecords} rows processed`
                    }
                    barClassName={job.status === 'FAILED' ? 'bg-red-500' : undefined}
                />
            </div>

            <dl className="mt-5 grid grid-cols-3 gap-3">
                <CountTile label="Total rows" value={job.totalRecords} tone="slate" />
                <CountTile label="Imported" value={job.validRecords} tone="green" />
                <CountTile label="Rejected" value={job.invalidRecords} tone="red" />
            </dl>

            {job.failureMessage && (
                <p className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700 ring-1 ring-red-100">
                    {job.failureMessage}
                </p>
            )}
        </Card>
    );
}

function CountTile({
    label,
    value,
    tone,
}: {
    label: string;
    value: number;
    tone: 'slate' | 'green' | 'red';
}) {
    const tones = {
        slate: 'text-slate-900',
        green: 'text-emerald-600',
        red: value > 0 ? 'text-red-600' : 'text-slate-400',
    };

    return (
        <div className="rounded-lg bg-slate-50 p-3 text-center">
            <dd className={cn('text-xl font-semibold tabular-nums', tones[tone])}>{value}</dd>
            <dt className="mt-0.5 text-xs text-slate-500">{label}</dt>
        </div>
    );
}
