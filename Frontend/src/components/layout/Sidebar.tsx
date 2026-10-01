import { NavLink } from 'react-router-dom';
import { GraduationCap, LogOut, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/cn';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { logoutThunk } from '@/store/slices/authSlice';
import { setSidebarOpen } from '@/store/slices/uiSlice';
import { displayName } from '@/types/auth';
import { NAV_BY_ROLE } from './navigation';

/**
 * Role-aware navigation.
 *
 * One component serves both breakpoints: a permanent rail from `lg` up, and a
 * slide-over drawer below it. Rendering two copies would mean two places to
 * forget a new link.
 */
export function Sidebar() {
    const dispatch = useAppDispatch();
    const user = useAppSelector((state) => state.auth.user);
    const open = useAppSelector((state) => state.ui.sidebarOpen);

    if (!user) return null;

    const items = NAV_BY_ROLE[user.role];
    const close = () => dispatch(setSidebarOpen(false));

    return (
        <>
            {/* Scrim. `lg:hidden` so it can never trap clicks on a desktop. */}
            {open && (
                <div
                    className="fixed inset-0 z-30 bg-slate-900/40 backdrop-blur-sm lg:hidden"
                    onClick={close}
                    aria-hidden
                />
            )}

            <aside
                className={cn(
                    'fixed inset-y-0 left-0 z-40 flex w-72 flex-col border-r border-slate-200 bg-white',
                    'transition-transform duration-200 lg:translate-x-0',
                    open ? 'translate-x-0' : '-translate-x-full',
                )}
            >
                <div className="flex h-16 items-center gap-2.5 border-b border-slate-200 px-5">
                    <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-blue-600 text-white">
                        <GraduationCap className="size-5" aria-hidden />
                    </span>
                    <div className="min-w-0">
                        <p className="truncate text-sm font-semibold text-slate-900">Result Intelligence</p>
                        <p className="text-xs text-slate-500">{roleLabel(user.role)}</p>
                    </div>
                    <button
                        type="button"
                        onClick={close}
                        className="ml-auto rounded-md p-1.5 text-slate-400 hover:bg-slate-100 lg:hidden"
                        aria-label="Close navigation"
                    >
                        <X className="size-5" aria-hidden />
                    </button>
                </div>

                <nav className="flex-1 space-y-1 overflow-y-auto p-3">
                    {items.map((item) => (
                        <NavLink
                            key={item.to}
                            to={item.to}
                            onClick={close}
                            className={({ isActive }) =>
                                cn(
                                    'flex items-start gap-3 rounded-lg px-3 py-2.5 transition',
                                    isActive
                                        ? 'bg-blue-50 text-blue-700 ring-1 ring-blue-100'
                                        : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900',
                                )
                            }
                        >
                            {({ isActive }) => (
                                <>
                                    <item.icon
                                        className={cn(
                                            'mt-0.5 size-5 shrink-0',
                                            isActive ? 'text-blue-600' : 'text-slate-400',
                                        )}
                                        aria-hidden
                                    />
                                    <span className="min-w-0">
                                        <span className="block text-sm font-medium">{item.label}</span>
                                        <span className="mt-0.5 block text-xs leading-snug text-slate-500">
                                            {item.description}
                                        </span>
                                    </span>
                                </>
                            )}
                        </NavLink>
                    ))}
                </nav>

                <div className="border-t border-slate-200 p-3">
                    <div className="rounded-lg bg-slate-50 px-3 py-2.5">
                        <p className="truncate text-sm font-medium text-slate-900">{displayName(user)}</p>
                        <p className="truncate text-xs text-slate-500">{user.email}</p>
                    </div>
                    <Button
                        variant="ghost"
                        size="sm"
                        fullWidth
                        className="mt-2 justify-start"
                        onClick={() => dispatch(logoutThunk())}
                        leftIcon={<LogOut className="size-4" aria-hidden />}
                    >
                        Sign out
                    </Button>
                </div>
            </aside>
        </>
    );
}

function roleLabel(role: string): string {
    return role.charAt(0) + role.slice(1).toLowerCase();
}
