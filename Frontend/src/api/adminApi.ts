import type { UploadAccepted, UploadJobView } from '@/types/admin';
import { api } from './client';

export const adminApi = {
    /**
     * Posts the CSV and returns immediately with a job id - the import runs on
     * a background thread, so the caller polls {@link job} for progress.
     *
     * @param onProgress fraction 0..1 of the bytes uploaded. This measures the
     *                   upload, not the import; the two are different phases and
     *                   the UI labels them separately.
     */
    upload(file: File, onProgress?: (fraction: number) => void): Promise<UploadAccepted> {
        const form = new FormData();
        form.append('file', file);

        return api
            .post<UploadAccepted>('/admin/upload', form, {
                // Let the browser set the multipart boundary; a hard-coded
                // Content-Type here would omit it and the server would reject
                // the body as unparseable.
                headers: { 'Content-Type': undefined },
                onUploadProgress: (event) => {
                    if (!onProgress) return;
                    // `total` is absent for a chunked body; report indeterminate
                    // progress as 0 rather than dividing by undefined.
                    const total = event.total ?? 0;
                    onProgress(total > 0 ? event.loaded / total : 0);
                },
            })
            .then((r) => r.data);
    },

    job(uploadJobId: number): Promise<UploadJobView> {
        return api.get<UploadJobView>(`/admin/upload/${uploadJobId}`).then((r) => r.data);
    },

    /** Upload history, newest first. `errors` is empty on these rows. */
    history(): Promise<UploadJobView[]> {
        return api.get<UploadJobView[]>('/admin/upload').then((r) => r.data);
    },
};
