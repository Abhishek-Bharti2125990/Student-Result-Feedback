import { LogOut, Menu } from 'lucide-react';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { logoutThunk } from '@/store/slices/authSlice';
import { toggleSidebar } from '@/store/slices/uiSlice';
import { displayName, initials } from '@/types/auth';
import { Badge } from '@/components/ui/Badge';
import { Breadcrumbs } from './Breadcrumbs';

const ROLE_TONE = {
    STUDENT: 'blue',
    TEACHER: 'violet',
    ADMIN: 'amber',
} as const;

export function Navbar() {
    const dispatch = useAppDispatch();
    const user = useAppSelector((state) => state.auth.user);

    return (
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-slate-200 bg-white/90 px-4 backdrop-blur sm:px-6">
            <button
                type="button"
                onClick={() => dispatch(toggleSidebar())}
                className="rounded-md p-2 text-slate-500 transition hover:bg-slate-100 lg:hidden"
                aria-label="Open navigation"
            >
                <Menu className="size-5" aria-hidden />
            </button>

            <Breadcrumbs />

            <div className="ml-auto flex items-center gap-3">
                {user && (
                    <>
                        <Badge tone={ROLE_TONE[user.role]} className="hidden sm:inline-flex">
                            {user.role}
                        </Badge>

                        <div className="hidden text-right sm:block">
                            <p className="text-sm font-medium leading-tight text-slate-900">
                                {displayName(user)}
                            </p>
                            <p className="text-xs leading-tight text-slate-500">{user.username}</p>
                        </div>

                        <span
                            className="grid size-9 place-items-center rounded-full bg-blue-600 text-xs font-semibold text-white"
                            title={displayName(user)}
                        >
                            {initials(user)}
                        </span>

                        <button
                            type="button"
                            onClick={() => dispatch(logoutThunk())}
                            className="rounded-md p-2 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
                            aria-label="Sign out"
                        >
                            <LogOut className="size-4" aria-hidden />
                        </button>
                    </>
                )}
            </div>
        </header>
    );
}
