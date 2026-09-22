import React from 'react';
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from './redux/hooks';
import { login, logout, type AuthUser } from './redux/slices/authSlice';

import AuthPage from './auth/AuthPage';
import StudentDashboard from './dashboard/StudentDashboard';
import TeacherDashboard from './dashboard/TeacherDashboard';
import Home from './home/Home';
import { Navbar } from './components/navbar';
import { ProtectedRoute } from './components/protectedRoute';

export default function App() {
    const dispatch = useAppDispatch();
    const navigate = useNavigate();

    // Redux state se current user nikalo
    const { user } = useAppSelector((state) => state.auth);

    // Login action trigger
    const handleLoginSuccess = (userData: AuthUser) => {
        dispatch(login(userData));
        if (userData.role === 'TEACHER') {
            navigate('/teacher-dashboard');
        } else {
            navigate('/student-dashboard');
        }
    };

    // Logout action trigger
    const handleLogout = () => {
        dispatch(logout());
        navigate('/login');
    };

    return (
        <div className="min-h-screen bg-slate-950 flex flex-col">
            <Navbar user={user} onLogout={handleLogout} />

            <div className="flex-1">
                <Routes>
                    <Route path="/" element={<Home />} />

                    <Route
                        path="/login"
                        element={
                            user ? (
                                <Navigate
                                    to={user.role === 'TEACHER' ? '/teacher-dashboard' : '/student-dashboard'}
                                    replace
                                />
                            ) : (
                                <AuthPage onLoginSuccess={handleLoginSuccess} />
                            )
                        }
                    />

                    <Route
                        path="/student-dashboard"
                        element={
                            <ProtectedRoute user={user} allowedRole="STUDENT">
                                <StudentDashboard />
                            </ProtectedRoute>
                        }
                    />

                    <Route
                        path="/teacher-dashboard"
                        element={
                            <ProtectedRoute user={user} allowedRole="TEACHER">
                                <TeacherDashboard />
                            </ProtectedRoute>
                        }
                    />

                    <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
            </div>
        </div>
    );
}