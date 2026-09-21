// src/App.tsx
import React, { useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import AuthPage from './components/auth';
import StudentDashboard from './dashboard/Student/StudentDashboard';
import TeacherDashboard from './dashboard/Teacher/TeacherDashboard';
import { Navbar } from './components/navbar';
import { ProtectedRoute } from './components/protectedRoute';

export interface AuthUser {
    name: string;
    email: string;
    role: 'STUDENT' | 'TEACHER';
    token?: string;
}

const AppContent: React.FC = () => {
    const [user, setUser] = useState<AuthUser | null>(() => {
        // Page refresh hone pe session preserve rakhne ke liye
        const saved = localStorage.getItem('authUser');
        return saved ? JSON.parse(saved) : null;
    });

    const navigate = useNavigate();

    // Login successful hone par ye call hoga
    const handleLoginSuccess = (userData: AuthUser) => {
        setUser(userData);
        localStorage.setItem('authUser', JSON.stringify(userData));
        if (userData.role === 'TEACHER') {
            navigate('/teacher-dashboard');
        } else {
            navigate('/student-dashboard');
        }
    };

    // Logout handler
    const handleLogout = () => {
        setUser(null);
        localStorage.removeItem('authUser');
        localStorage.removeItem('token');
        navigate('/login');
    };

    return (
        <div className="min-h-screen bg-slate-950 flex flex-col">
            {/* Global Navbar */}
            <Navbar user={user} onLogout={handleLogout} />

            {/* App Routes */}
            <div className="flex-1">
                <Routes>
                    {/* Default Route */}
                    <Route
                        path="/"
                        element={
                            user ? (
                                <Navigate to={user.role === 'TEACHER' ? '/teacher-dashboard' : '/student-dashboard'} replace />
                            ) : (
                                <Navigate to="/login" replace />
                            )
                        }
                    />

                    {/* Login / Signup Route */}
                    <Route
                        path="/login"
                        element={
                            user ? (
                                <Navigate to={user.role === 'TEACHER' ? '/teacher-dashboard' : '/student-dashboard'} replace />
                            ) : (
                                <AuthPage onLoginSuccess={handleLoginSuccess} />
                            )
                        }
                    />

                    {/* Protected Student Dashboard */}
                    <Route
                        path="/student-dashboard"
                        element={
                            <ProtectedRoute user={user} allowedRole="STUDENT">
                                <StudentDashboard />
                            </ProtectedRoute>
                        }
                    />

                    {/* Protected Teacher Dashboard */}
                    <Route
                        path="/teacher-dashboard"
                        element={
                            <ProtectedRoute user={user} allowedRole="TEACHER">
                                <TeacherDashboard />
                            </ProtectedRoute>
                        }
                    />

                    {/* 404 Fallback */}
                    <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
            </div>
        </div>
    );
};

export default function App() {
    return (
        <BrowserRouter>
            <AppContent />
        </BrowserRouter>
    );
}