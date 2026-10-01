import { useEffect, type ReactNode } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { useAppDispatch } from '@/store/hooks';
import { setSidebarOpen } from '@/store/slices/uiSlice';
import { Navbar } from './Navbar';
import { NotificationArea } from './NotificationArea';
import { Sidebar } from './Sidebar';

/** The shell every authenticated page renders inside. */
export function AppLayout() {
    const dispatch = useAppDispatch();
    const { pathname } = useLocation();

    // Close the mobile drawer on navigation. Without this, tapping a link
    // leaves the new page hidden behind the sidebar that opened it.
    useEffect(() => {
        dispatch(setSidebarOpen(false));
    }, [dispatch, pathname]);

    return (
        <div className="min-h-screen bg-slate-50">
            <Sidebar />
            <NotificationArea />

            {/* Offset matches the sidebar width at `lg` and above. */}
            <div className="lg:pl-72">
                <Navbar />
                <main className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 lg:py-8">
                    <Outlet />
                </main>
            </div>
        </div>
    );
}

interface PageHeaderProps {
    title: string;
    description?: string;
    /** Filters or actions, wrapping beneath the title on narrow screens. */
    action?: ReactNode;
}

export function PageHeader({ title, description, action }: PageHeaderProps) {
    return (
        <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div className="min-w-0">
                <h1 className="text-xl font-semibold tracking-tight text-slate-900 sm:text-2xl">
                    {title}
                </h1>
                {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
            </div>
            {action && <div className="shrink-0">{action}</div>}
        </div>
    );
}
