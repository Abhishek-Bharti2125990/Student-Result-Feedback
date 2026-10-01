import { useCallback, useEffect, useState } from 'react';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { fetchUploadJob } from '@/store/slices/adminSlice';
import { isInFlight, type UploadJobView } from '@/types/admin';

/** How often to ask the server how the import is going. */
const POLL_INTERVAL_MS = 1500;

interface UploadJobPolling {
    /** The job, once the store holds this id's view; null before the first answer. */
    job: UploadJobView | null;
    /** Set when the last poll failed. Polling has stopped; call {@link retry}. */
    error: string | null;
    retry: () => void;
}

/**
 * Polls one import until it reaches a terminal state.
 *
 * Each request is scheduled only after the previous one answers, so a slow
 * server never has polls stacking up behind each other. The effect is keyed on
 * "still running" rather than on the job object: every poll returns a new
 * object, and keying on it would restart the loop - firing the next request
 * immediately instead of after the interval.
 *
 * A failed poll stops the loop instead of retrying blindly. An unknown job id is
 * a 404 forever, and silently hammering it is worse than showing the error.
 */
export function useUploadJobPolling(jobId: number | null): UploadJobPolling {
    const dispatch = useAppDispatch();
    const { activeJob, jobError } = useAppSelector((state) => state.admin);
    const [attempt, setAttempt] = useState(0);

    // The store holds one job at a time; ignore it if it is a different one.
    const job = activeJob && activeJob.id === jobId ? activeJob : null;
    const running = job === null || isInFlight(job.status);

    useEffect(() => {
        if (jobId === null || !running) return;

        let cancelled = false;
        let timer: number | undefined;

        const tick = async () => {
            const result = await dispatch(fetchUploadJob(jobId));
            if (cancelled || fetchUploadJob.rejected.match(result)) return;
            timer = window.setTimeout(() => void tick(), POLL_INTERVAL_MS);
        };
        void tick();

        return () => {
            cancelled = true;
            window.clearTimeout(timer);
        };
    }, [dispatch, jobId, running, attempt]);

    const retry = useCallback(() => setAttempt((count) => count + 1), []);

    return { job, error: jobId === null ? null : jobError, retry };
}
