// src/components/ProtectedRoute.tsx
import React from 'react';
import { Navigate } from 'react-router-dom';

interface ProtectedRouteProps {
    user: { role: 'STUDENT' | 'TEACHER' } | null;
    allowedRole: 'STUDENT' | 'TEACHER';
    children: React.ReactElement;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ user, allowedRole, children }) => {
    if (!user) {
        return <Navigate to="/login" replace />;
    }

    if (user.role !== allowedRole) {
        return <Navigate to={user.role === 'TEACHER' ? '/teacher-dashboard' : '/student-dashboard'} replace />;
    }

    return children;
};