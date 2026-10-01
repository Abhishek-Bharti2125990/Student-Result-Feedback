import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { FileSpreadsheet, History, Info, UploadCloud } from 'lucide-react';
import { PageHeader } from '@/components/layout/AppLayout';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { ProgressBar } from '@/components/ui/ProgressBar';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { clearUpload, fetchUploadJob, uploadCsv } from '@/store/slices/adminSlice';
import { notify } from '@/store/slices/uiSlice';
import { EXPECTED_CSV_HEADER, isInFlight } from '@/types/admin';
import { CsvDropzone } from './CsvDropzone';
import { UploadErrorReport } from './UploadErrorReport';
import { UploadStatusPanel } from './UploadStatusPanel';

/** How often to ask the server how the import is going. */
const POLL_INTERVAL_MS = 1500;

export function AdminUploadPage() {
    const dispatch = useAppDispatch();
    const { uploading, uploadFraction, uploadError, activeJob, activeJobId } = useAppSelector(
        (state) => state.admin,
    );

    const [file, setFile] = useState<File | null>(null);

    /**
     * Polling is driven by an effect keyed on the job id and its status.
     *
     * The interval is cleared the moment the job reaches a terminal state, so a
     * finished import stops generating requests - a dashboard left open on a
     * completed job should be silent, not hammering the API all afternoon.
     */
    const pollRef = useRef<number | null>(null);

    useEffect(() => {
        if (activeJobId === null) return;

        const stillRunning = activeJob === null || isInFlight(activeJob.status);
        if (!stillRunning) {
            return;
        }

        void dispatch(fetchUploadJob(activeJobId));
        pollRef.current = window.setInterval(() => {
            void dispatch(fetchUploadJob(activeJobId));
        }, POLL_INTERVAL_MS);

        return () => {
            if (pollRef.current !== null) {
                window.clearInterval(pollRef.current);
                pollRef.current = null;
            }
        };
    }, [dispatch, activeJobId, activeJob?.status, activeJob]);

    // Announce the outcome once, when the job reaches a terminal state.
    const announcedRef = useRef<number | null>(null);
    useEffect(() => {
        if (!activeJob || isInFlight(activeJob.status)) return;
        if (announcedRef.current === activeJob.id) return;
        announcedRef.current = activeJob.id;

        if (activeJob.status === 'FAILED') {
            dispatch(
                notify({
                    kind: 'error',
                    title: 'Import failed',
                    message: activeJob.failureMessage ?? 'No rows were imported.',
                }),
            );
        } else if (activeJob.status === 'COMPLETED_WITH_ERRORS') {
            dispatch(
                notify({
                    kind: 'info',
                    title: 'Imported with errors',
                    message: `${activeJob.validRecords} rows imported, ${activeJob.invalidRecords} rejected.`,
                }),
            );
        } else {
            dispatch(
                notify({
                    kind: 'success',
                    title: 'Import complete',
                    message: `${activeJob.validRecords} rows imported.`,
                }),
            );
        }
    }, [dispatch, activeJob]);

    const submit = () => {
        if (!file) return;
        void dispatch(uploadCsv(file))
            .unwrap()
            .then((accepted) => {
                dispatch(
                    notify({
                        kind: 'info',
                        title: `Upload accepted — job ${accepted.uploadJobId}`,
                        message: `${accepted.dataRowCount} data rows queued for processing.`,
                    }),
                );
                setFile(null);
            })
            .catch(() => undefined);
    };

    const startOver = () => {
        dispatch(clearUpload());
        setFile(null);
        announcedRef.current = null;
    };

    return (
        <>
            <PageHeader
                title="Upload results"
                description="Import a results CSV. Marks, analytics and AI feedback are all produced by one job."
                action={
                    <Link to="/admin/uploads">
                        <Button variant="outline" size="sm" leftIcon={<History className="size-3.5" aria-hidden />}>
                            Upload history
                        </Button>
                    </Link>
                }
            />

            <div className="grid gap-6 lg:grid-cols-3">
                <div className="space-y-6 lg:col-span-2">
                    <Card>
                        <CardHeader
                            title="Choose a file"
                            subtitle="One row per topic, ten columns"
                            icon={<UploadCloud className="size-4" aria-hidden />}
                        />

                        <div className="mt-4">
                            <CsvDropzone
                                file={file}
                                disabled={uploading}
                                onFileSelected={setFile}
                                onClear={() => setFile(null)}
                                onRejected={(rejection) =>
                                    dispatch(
                                        notify({
                                            kind: 'error',
                                            title: 'File not accepted',
                                            message: rejection.reason,
                                        }),
                                    )
                                }
                            />
                        </div>

                        {uploading && (
                            <div className="mt-4">
                                <ProgressBar value={uploadFraction} label="Sending file to the server" />
                            </div>
                        )}

                        {uploadError && (
                            <p
                                className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700 ring-1 ring-red-100"
                                role="alert"
                            >
                                {uploadError}
                            </p>
                        )}

                        <div className="mt-5 flex flex-wrap gap-2.5">
                            <Button
                                onClick={submit}
                                disabled={!file}
                                loading={uploading}
                                leftIcon={<UploadCloud className="size-4" aria-hidden />}
                            >
                                {uploading ? 'Uploading…' : 'Start import'}
                            </Button>
                            {(activeJobId !== null || file) && (
                                <Button variant="ghost" onClick={startOver} disabled={uploading}>
                                    Start over
                                </Button>
                            )}
                        </div>
                    </Card>

                    {activeJob && <UploadStatusPanel job={activeJob} />}
                    {activeJob && <UploadErrorReport errors={activeJob.errors} />}
                </div>

                {/* -- Format reference ---------------------------------------- */}
                <div className="space-y-6">
                    <Card>
                        <CardHeader
                            title="Expected format"
                            subtitle="The header must match exactly"
                            icon={<FileSpreadsheet className="size-4" aria-hidden />}
                        />
                        <ol className="mt-4 space-y-1.5">
                            {EXPECTED_CSV_HEADER.map((column, index) => (
                                <li key={column} className="flex items-center gap-2.5 text-sm">
                                    <span className="grid size-5 shrink-0 place-items-center rounded bg-slate-100 text-xs font-medium tabular-nums text-slate-500">
                                        {index + 1}
                                    </span>
                                    <code className="font-mono text-xs text-slate-700">{column}</code>
                                </li>
                            ))}
                        </ol>

                        <div className="mt-4 overflow-x-auto rounded-lg bg-slate-900 p-3">
                            <code className="whitespace-nowrap font-mono text-xs text-slate-300">
                                1001,Ayushman,10,A,Midterm,Mathematics,Algebra,Quadratic Equations,10,25
                            </code>
                        </div>
                    </Card>

                    <Card className="bg-blue-50/60 ring-blue-100">
                        <div className="flex items-start gap-3">
                            <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-blue-100 text-blue-700">
                                <Info className="size-4" aria-hidden />
                            </span>
                            <div className="text-sm leading-relaxed text-slate-700">
                                <p className="font-semibold text-slate-900">What the import does</p>
                                <ol className="mt-2 list-decimal space-y-1 pl-4 text-slate-600">
                                    <li>Validates the header, once, for the whole file</li>
                                    <li>Creates any student, exam, subject or topic it does not know</li>
                                    <li>Writes topic marks and totals each subject from them</li>
                                    <li>Ranks every class and stores each student's standing</li>
                                    <li>Generates the student and teacher feedback documents</li>
                                </ol>
                                <p className="mt-2.5 text-xs text-slate-500">
                                    A bad row is rejected on its own and reported with its line number;
                                    the rest of the file still imports.
                                </p>
                            </div>
                        </div>
                    </Card>
                </div>
            </div>
        </>
    );
}
