import { useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { History, RefreshCw, UploadCloud } from 'lucide-react';
import { PageHeader } from '@/components/layout/AppLayout';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { DataTable, type Column } from '@/components/ui/DataTable';
import { ErrorState } from '@/components/ui/ErrorState';
import { CardSkeleton } from '@/components/ui/Skeleton';
import { cn } from '@/lib/cn';
import { dateTime } from '@/lib/format';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { fetchUploadHistory } from '@/store/slices/adminSlice';
import type { UploadJobView } from '@/types/admin';
import { UploadStatusBadge } from './UploadStatusPanel';

export function AdminUploadsPage() {
    const dispatch = useAppDispatch();
    const navigate = useNavigate();
    const { history, historyLoading, historyError } = useAppSelector((state) => state.admin);

    useEffect(() => {
        void dispatch(fetchUploadHistory());
    }, [dispatch]);

    // The history rows carry an empty `errors` array - only the single-job
    // endpoint has the report - so a row opens the job's own status page.
    const select = (job: UploadJobView) => navigate(`/admin/uploads/${job.id}`);

    const columns: Array<Column<UploadJobView>> = [
        {
            key: 'id',
            header: 'Job',
            className: 'w-16 font-mono text-xs',
            render: (job) => job.id,
        },
        {
            key: 'filename',
            header: 'File',
            render: (job) => (
                <span className="font-medium text-slate-900">{job.filename}</span>
            ),
        },
        {
            key: 'status',
            header: 'Status',
            render: (job) => <UploadStatusBadge status={job.status} />,
        },
        {
            key: 'rows',
            header: 'Rows',
            hideOnMobile: true,
            render: (job) => (
                <span className="tabular-nums">
                    <span className="text-emerald-600">{job.validRecords}</span>
                    {' / '}
                    <span className={cn(job.invalidRecords > 0 ? 'text-red-600' : 'text-slate-400')}>
                        {job.invalidRecords}
                    </span>
                    {' of '}
                    {job.totalRecords}
                </span>
            ),
        },
        {
            key: 'created',
            header: 'Started',
            hideOnMobile: true,
            className: 'text-xs text-slate-500 whitespace-nowrap',
            render: (job) => dateTime(job.createdAt),
        },
    ];

    if (historyLoading && history.length === 0) {
        return (
            <>
                <PageHeader title="Upload history" description="Loading past imports…" />
                <CardSkeleton lines={6} />
            </>
        );
    }

    return (
        <>
            <PageHeader
                title="Upload history"
                description="Every import, with its record counts and error report"
                action={
                    <div className="flex gap-2">
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={() => void dispatch(fetchUploadHistory())}
                            loading={historyLoading}
                            leftIcon={<RefreshCw className="size-3.5" aria-hidden />}
                        >
                            Refresh
                        </Button>
                        <Link to="/admin/upload">
                            <Button size="sm" leftIcon={<UploadCloud className="size-3.5" aria-hidden />}>
                                New upload
                            </Button>
                        </Link>
                    </div>
                }
            />

            {historyError ? (
                <ErrorState
                    message={historyError}
                    onRetry={() => void dispatch(fetchUploadHistory())}
                    retrying={historyLoading}
                />
            ) : (
                <div className="space-y-6">
                    <Card flush>
                        <div className="p-5 pb-0">
                            <CardHeader
                                title="Imports"
                                subtitle={
                                    history.length > 0
                                        ? 'Newest first — select a row for its processing status and error report'
                                        : undefined
                                }
                                icon={<History className="size-4" aria-hidden />}
                            />
                        </div>
                        <div className="mt-4 px-5 pb-1">
                            <DataTable
                                columns={columns}
                                rows={history}
                                rowKey={(job) => job.id}
                                onRowClick={select}
                                emptyTitle="No uploads yet"
                                emptyDescription="Import a results CSV to see it listed here."
                            />
                        </div>
                    </Card>
                </div>
            )}
        </>
    );
}
