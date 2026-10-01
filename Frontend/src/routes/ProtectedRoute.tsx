import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAppSelector } from '@/store/hooks';
import { LANDING_BY_ROLE } from '@/components/layout/navigation';
import type { Role } from '@/types/common';

interface ProtectedRouteProps {
    /** Roles allowed through. Omit to require only a valid session. */
    allow?: Role[];
    children: ReactNode;
}

/**
 * Gate on session and role.
 *
 * Two distinct redirects, because they are two different situations: a signed-out
 * visitor goes to /login with the path they wanted remembered, while a signed-in
 * user on the wrong role's page is sent to their own landing page. Sending the
 * second case to /login would look like being logged out at random.
 *
 * This is convenience and routing, not security. The backend enforces every one
 * of these rules again, which is the enforcement that counts - this only stops
 * the UI showing a page it cannot populate.
 */
export function ProtectedRoute({ allow, children }: ProtectedRouteProps) {
    const { isAuthenticated, user } = useAppSelector((state) => state.auth);
    const location = useLocation();

    if (!isAuthenticated || !user) {
        return <Navigate to="/login" replace state={{ from: location.pathname }} />;
    }

    if (allow && !allow.includes(user.role)) {
        return <Navigate to={LANDING_BY_ROLE[user.role]} replace />;
    }

    return <>{children}</>;
}

/** Sends a signed-in user to their role's home, and everyone else to /login. */
export function RoleLanding() {
    const { isAuthenticated, user } = useAppSelector((state) => state.auth);

    if (!isAuthenticated || !user) {
        return <Navigate to="/login" replace />;
    }
    return <Navigate to={LANDING_BY_ROLE[user.role]} replace />;
}
