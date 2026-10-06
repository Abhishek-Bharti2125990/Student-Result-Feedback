import { Suspense, lazy } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { AppLayout } from '@/components/layout/AppLayout';
import { Loader } from '@/components/ui/Loader';
import { LoginPage } from '@/features/auth/LoginPage';
import { ProtectedRoute, RoleLanding } from '@/routes/ProtectedRoute';

/*
 * The authenticated pages are split out of the initial bundle.
 *
 * Recharts is the single largest dependency here and it is not needed to render
 * the login screen - which is the only page an unauthenticated visitor ever
 * sees, and therefore the one whose first paint matters most. Login stays in
 * the main chunk; everything behind it arrives on navigation.
 */
const StudentDashboardPage = lazy(() =>
    import('@/features/student/StudentDashboardPage').then((m) => ({ default: m.StudentDashboardPage })),
);
const StudentFeedbackPage = lazy(() =>
    import('@/features/student/StudentFeedbackPage').then((m) => ({ default: m.StudentFeedbackPage })),
);
const StudentResourcesPage = lazy(() =>
    import('@/features/student/StudentResourcesPage').then((m) => ({ default: m.StudentResourcesPage })),
);
const TeacherDashboardPage = lazy(() =>
    import('@/features/teacher/TeacherDashboardPage').then((m) => ({ default: m.TeacherDashboardPage })),
);
const AdminUploadPage = lazy(() =>
    import('@/features/admin/AdminUploadPage').then((m) => ({ default: m.AdminUploadPage })),
);
const AdminUploadsPage = lazy(() =>
    import('@/features/admin/AdminUploadsPage').then((m) => ({ default: m.AdminUploadsPage })),
);
const AdminUploadStatusPage = lazy(() =>
    import('@/features/admin/AdminUploadStatusPage').then((m) => ({ default: m.AdminUploadStatusPage })),
);
const AdminUsersPage = lazy(() =>
    import('@/features/admin/AdminUsersPage').then((m) => ({ default: m.AdminUsersPage })),
);

/**
 * The route table.
 *
 * Every authenticated route sits inside one `AppLayout` element rather than
 * each page rendering its own shell, so the sidebar and navbar are not
 * remounted on navigation - which would reset the mobile drawer and flash the
 * chrome on every click. The `Suspense` boundary is inside the layout for the
 * same reason: a lazy page loads with the navigation already on screen.
 *
 * `/student/**` admits staff as well as students: the pages take an optional
 * `studentId`, and a teacher looking at one student's card wants exactly this
 * screen. The row-level check is the backend's, not this table's.
 */
export default function App() {
    return (
        <Routes>
            <Route path="/login" element={<LoginPage />} />

            <Route
                element={
                    <ProtectedRoute>
                        <AppLayout />
                    </ProtectedRoute>
                }
            >
                <Route
                    path="/student/dashboard"
                    element={
                        <ProtectedRoute allow={['STUDENT', 'TEACHER', 'ADMIN']}>
                            <Suspense fallback={<Loader label="Loading your dashboard…" />}>
                                <StudentDashboardPage />
                            </Suspense>
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/student/feedback"
                    element={
                        <ProtectedRoute allow={['STUDENT', 'TEACHER', 'ADMIN']}>
                            <Suspense fallback={<Loader label="Loading your feedback…" />}>
                                <StudentFeedbackPage />
                            </Suspense>
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/student/resources"
                    element={
                        <ProtectedRoute allow={['STUDENT', 'TEACHER', 'ADMIN']}>
                            <Suspense fallback={<Loader label="Loading resources…" />}>
                                <StudentResourcesPage />
                            </Suspense>
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="/teacher/dashboard"
                    element={
                        <ProtectedRoute allow={['TEACHER', 'ADMIN']}>
                            <Suspense fallback={<Loader label="Loading the class dashboard…" />}>
                                <TeacherDashboardPage />
                            </Suspense>
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="/admin/upload"
                    element={
                        <ProtectedRoute allow={['ADMIN']}>
                            <Suspense fallback={<Loader label="Loading…" />}>
                                <AdminUploadPage />
                            </Suspense>
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/admin/uploads"
                    element={
                        <ProtectedRoute allow={['ADMIN']}>
                            <Suspense fallback={<Loader label="Loading…" />}>
                                <AdminUploadsPage />
                            </Suspense>
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/admin/uploads/:jobId"
                    element={
                        <ProtectedRoute allow={['ADMIN']}>
                            <Suspense fallback={<Loader label="Loading…" />}>
                                <AdminUploadStatusPage />
                            </Suspense>
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/admin/users"
                    element={
                        <ProtectedRoute allow={['ADMIN']}>
                            <Suspense fallback={<Loader label="Loading accounts…" />}>
                                <AdminUsersPage />
                            </Suspense>
                        </ProtectedRoute>
                    }
                />
            </Route>

            {/* "/" sends each role to its own home; anything unknown follows. */}
            <Route path="/" element={<RoleLanding />} />
            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    );
}
