import React, { useState } from 'react';
import {
    GraduationCap,
    BookOpen,
    Mail,
    Lock,
    User,
    IdCard,
    Building,
    Eye,
    EyeOff,
    ArrowRight,
    Sparkles,
    AlertCircle
} from 'lucide-react';

export type Role = 'STUDENT' | 'TEACHER';
type AuthMode = 'LOGIN' | 'SIGNUP';

export interface AuthUser {
    name: string;
    email: string;
    role: Role;
    rollNo?: string;
    semester?: string;
    department?: string;
    employeeId?: string;
}

interface AuthPageProps {
    onLoginSuccess: (user: AuthUser) => void;
}

export const AuthPage: React.FC<AuthPageProps> = ({ onLoginSuccess }) => {
    const [mode, setMode] = useState<AuthMode>('LOGIN');
    const [role, setRole] = useState<Role>('STUDENT');
    const [showPassword, setShowPassword] = useState<boolean>(false);
    const [loading, setLoading] = useState<boolean>(false);
    const [errorMessage, setErrorMessage] = useState<string>('');

    // Form State
    const [formData, setFormData] = useState({
        name: '',
        email: '',
        password: '',
        rollNo: '',
        semester: '1',
        department: '',
        employeeId: ''
    });

    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        const { name, value } = e.target;
        setErrorMessage('');
        setFormData((prev) => ({ ...prev, [name]: value }));
    };

    const handleRoleChange = (newRole: Role) => {
        setRole(newRole);
        setErrorMessage('');
    };

    // 1-Click Demo Login (For Fast Testing)
    const handleQuickDemo = (demoRole: Role) => {
        const demoUser: AuthUser = demoRole === 'STUDENT'
            ? {
                name: 'Rahul Sharma',
                email: 'rahul.student@univ.edu',
                role: 'STUDENT',
                rollNo: '21CS042',
                semester: '5'
            }
            : {
                name: 'Dr. Anjali Roy',
                email: 'anjali.prof@univ.edu',
                role: 'TEACHER',
                department: 'Computer Science',
                employeeId: 'EMP-902'
            };

        onLoginSuccess(demoUser);
    };

    // Form Submission via LocalStorage
    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setErrorMessage('');
        setLoading(true);

        // Simulate small network delay for realism
        await new Promise((resolve) => setTimeout(resolve, 600));

        // Get existing users from localStorage
        const storedUsersRaw = localStorage.getItem('registered_users');
        const registeredUsers: any[] = storedUsersRaw ? JSON.parse(storedUsersRaw) : [];

        if (mode === 'SIGNUP') {
            // 1. Check if email already exists
            const userExists = registeredUsers.some((u) => u.email.toLowerCase() === formData.email.toLowerCase());
            if (userExists) {
                setErrorMessage('User with this email already exists! Please Sign In.');
                setLoading(false);
                return;
            }

            // 2. Save new user
            const newUser = {
                name: formData.name,
                email: formData.email,
                password: formData.password,
                role: role,
                rollNo: formData.rollNo,
                semester: formData.semester,
                department: formData.department,
                employeeId: formData.employeeId
            };

            registeredUsers.push(newUser);
            localStorage.setItem('registered_users', JSON.stringify(registeredUsers));

            // Auto login after signup
            const { password, ...safeUser } = newUser;
            onLoginSuccess(safeUser as AuthUser);

        } else {
            // LOGIN LOGIC
            const matchedUser = registeredUsers.find(
                (u) => u.email.toLowerCase() === formData.email.toLowerCase() && u.password === formData.password
            );

            if (!matchedUser) {
                setErrorMessage('Invalid email or password! (Try quick demo buttons below)');
                setLoading(false);
                return;
            }

            const { password, ...safeUser } = matchedUser;
            onLoginSuccess(safeUser as AuthUser);
        }

        setLoading(false);
    };

    return (
        <div className="min-h-screen bg-slate-950 flex items-center justify-center p-4 sm:p-6 lg:p-8 relative overflow-hidden">
            {/* Background Ambient Glows */}
            <div className="absolute top-1/4 left-1/4 w-80 h-80 bg-indigo-600/15 rounded-full blur-3xl pointer-events-none" />
            <div className="absolute bottom-1/4 right-1/4 w-80 h-80 bg-cyan-600/15 rounded-full blur-3xl pointer-events-none" />

            {/* Main Container */}
            <div className="relative w-full max-w-md bg-slate-900/90 backdrop-blur-xl border border-slate-800 rounded-3xl p-6 sm:p-8 shadow-2xl space-y-6">

                {/* Header Title */}
                <div className="text-center">
                    <div className="inline-flex p-3 bg-indigo-500/10 border border-indigo-500/20 rounded-2xl mb-3 text-indigo-400">
                        {role === 'STUDENT' ? <GraduationCap size={32} /> : <BookOpen size={32} />}
                    </div>
                    <h2 className="text-2xl font-bold text-white tracking-tight">
                        {mode === 'LOGIN' ? 'Welcome Back!' : 'Create Account'}
                    </h2>
                    <p className="text-xs sm:text-sm text-slate-400 mt-1">
                        {mode === 'LOGIN'
                            ? 'Enter your credentials to access the portal'
                            : 'Sign up to access course schedules & portal'}
                    </p>
                </div>

                {/* Tab Switcher: Sign In vs Sign Up */}
                <div className="flex bg-slate-950/70 p-1.5 rounded-xl border border-slate-800">
                    <button
                        type="button"
                        onClick={() => { setMode('LOGIN'); setErrorMessage(''); }}
                        className={`flex-1 py-2 text-xs sm:text-sm font-semibold rounded-lg transition-all ${mode === 'LOGIN'
                                ? 'bg-indigo-600 text-white shadow'
                                : 'text-slate-400 hover:text-white'
                            }`}
                    >
                        Sign In
                    </button>
                    <button
                        type="button"
                        onClick={() => { setMode('SIGNUP'); setErrorMessage(''); }}
                        className={`flex-1 py-2 text-xs sm:text-sm font-semibold rounded-lg transition-all ${mode === 'SIGNUP'
                                ? 'bg-indigo-600 text-white shadow'
                                : 'text-slate-400 hover:text-white'
                            }`}
                    >
                        Sign Up
                    </button>
                </div>

                {/* Error Alert Box */}
                {errorMessage && (
                    <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs flex items-center gap-2">
                        <AlertCircle size={16} className="shrink-0" />
                        <span>{errorMessage}</span>
                    </div>
                )}

                {/* Role Selector (Sign Up only) */}
                {mode === 'SIGNUP' && (
                    <div>
                        <label className="block text-[11px] font-semibold text-slate-400 uppercase tracking-wider mb-2">
                            Select Your Role
                        </label>
                        <div className="grid grid-cols-2 gap-3">
                            <button
                                type="button"
                                onClick={() => handleRoleChange('STUDENT')}
                                className={`flex items-center justify-center gap-2 p-3 rounded-xl border transition-all text-xs sm:text-sm ${role === 'STUDENT'
                                        ? 'border-indigo-500 bg-indigo-500/10 text-indigo-400 font-semibold shadow-inner'
                                        : 'border-slate-800 bg-slate-950/40 text-slate-400 hover:border-slate-700'
                                    }`}
                            >
                                <GraduationCap size={18} />
                                <span>Student</span>
                            </button>

                            <button
                                type="button"
                                onClick={() => handleRoleChange('TEACHER')}
                                className={`flex items-center justify-center gap-2 p-3 rounded-xl border transition-all text-xs sm:text-sm ${role === 'TEACHER'
                                        ? 'border-indigo-500 bg-indigo-500/10 text-indigo-400 font-semibold shadow-inner'
                                        : 'border-slate-800 bg-slate-950/40 text-slate-400 hover:border-slate-700'
                                    }`}
                            >
                                <BookOpen size={18} />
                                <span>Teacher</span>
                            </button>
                        </div>
                    </div>
                )}

                {/* Auth Form */}
                <form onSubmit={handleSubmit} className="space-y-3.5">

                    {/* Name Field (Sign Up Only) */}
                    {mode === 'SIGNUP' && (
                        <div>
                            <label className="block text-xs font-medium text-slate-300 mb-1">Full Name</label>
                            <div className="relative">
                                <User size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500" />
                                <input
                                    type="text"
                                    name="name"
                                    required
                                    placeholder="Rahul Sharma"
                                    value={formData.name}
                                    onChange={handleInputChange}
                                    className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-10 pr-4 py-2 text-sm text-white placeholder-slate-600 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition"
                                />
                            </div>
                        </div>
                    )}

                    {/* Email Field */}
                    <div>
                        <label className="block text-xs font-medium text-slate-300 mb-1">Email Address</label>
                        <div className="relative">
                            <Mail size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500" />
                            <input
                                type="email"
                                name="email"
                                required
                                placeholder="user@university.edu"
                                value={formData.email}
                                onChange={handleInputChange}
                                className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-10 pr-4 py-2 text-sm text-white placeholder-slate-600 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition"
                            />
                        </div>
                    </div>

                    {/* Student Role Extra Fields */}
                    {mode === 'SIGNUP' && role === 'STUDENT' && (
                        <div className="grid grid-cols-2 gap-3">
                            <div>
                                <label className="block text-xs font-medium text-slate-300 mb-1">Roll Number</label>
                                <div className="relative">
                                    <IdCard size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500" />
                                    <input
                                        type="text"
                                        name="rollNo"
                                        required
                                        placeholder="21CS042"
                                        value={formData.rollNo}
                                        onChange={handleInputChange}
                                        className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-9 pr-3 py-2 text-sm text-white placeholder-slate-600 focus:outline-none focus:border-indigo-500"
                                    />
                                </div>
                            </div>
                            <div>
                                <label className="block text-xs font-medium text-slate-300 mb-1">Semester</label>
                                <select
                                    name="semester"
                                    value={formData.semester}
                                    onChange={handleInputChange}
                                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500"
                                >
                                    {[1, 2, 3, 4, 5, 6, 7, 8].map((sem) => (
                                        <option key={sem} value={sem} className="bg-slate-900 text-white">
                                            Sem {sem}
                                        </option>
                                    ))}
                                </select>
                            </div>
                        </div>
                    )}

                    {/* Teacher Role Extra Fields */}
                    {mode === 'SIGNUP' && role === 'TEACHER' && (
                        <div className="grid grid-cols-2 gap-3">
                            <div>
                                <label className="block text-xs font-medium text-slate-300 mb-1">Department</label>
                                <div className="relative">
                                    <Building size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500" />
                                    <input
                                        type="text"
                                        name="department"
                                        required
                                        placeholder="CSE / IT"
                                        value={formData.department}
                                        onChange={handleInputChange}
                                        className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-9 pr-3 py-2 text-sm text-white placeholder-slate-600 focus:outline-none focus:border-indigo-500"
                                    />
                                </div>
                            </div>
                            <div>
                                <label className="block text-xs font-medium text-slate-300 mb-1">Emp ID</label>
                                <div className="relative">
                                    <IdCard size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500" />
                                    <input
                                        type="text"
                                        name="employeeId"
                                        required
                                        placeholder="EMP-102"
                                        value={formData.employeeId}
                                        onChange={handleInputChange}
                                        className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-9 pr-3 py-2 text-sm text-white placeholder-slate-600 focus:outline-none focus:border-indigo-500"
                                    />
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Password Field */}
                    <div>
                        <label className="block text-xs font-medium text-slate-300 mb-1">Password</label>
                        <div className="relative">
                            <Lock size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500" />
                            <input
                                type={showPassword ? 'text' : 'password'}
                                name="password"
                                required
                                placeholder="••••••••"
                                value={formData.password}
                                onChange={handleInputChange}
                                className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-10 pr-10 py-2 text-sm text-white placeholder-slate-600 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition"
                            />
                            <button
                                type="button"
                                onClick={() => setShowPassword(!showPassword)}
                                className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition"
                            >
                                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                            </button>
                        </div>
                    </div>

                    {/* Submit Button */}
                    <button
                        type="submit"
                        disabled={loading}
                        className="w-full mt-2 flex items-center justify-center gap-2 bg-indigo-600 hover:bg-indigo-500 text-white font-medium py-2.5 rounded-xl shadow-lg shadow-indigo-600/30 transition disabled:opacity-60"
                    >
                        {loading ? (
                            <span className="inline-block w-4 h-4 border-2 border-white/20 border-t-white rounded-full animate-spin" />
                        ) : (
                            <>
                                <span>{mode === 'LOGIN' ? 'Sign In' : 'Create Account'}</span>
                                <ArrowRight size={16} />
                            </>
                        )}
                    </button>
                </form>

                {/* Quick Demo Test Buttons */}
                <div className="pt-2 border-t border-slate-800/80">
                    <p className="text-[11px] text-center text-slate-500 mb-2.5 flex items-center justify-center gap-1">
                        <Sparkles size={12} className="text-amber-400" /> Quick Testing Credentials
                    </p>
                    <div className="grid grid-cols-2 gap-2">
                        <button
                            type="button"
                            onClick={() => handleQuickDemo('STUDENT')}
                            className="px-2.5 py-1.5 bg-slate-950 hover:bg-slate-800 border border-slate-800 rounded-xl text-xs text-slate-300 font-medium transition"
                        >
                            🚀 1-Click Student
                        </button>
                        <button
                            type="button"
                            onClick={() => handleQuickDemo('TEACHER')}
                            className="px-2.5 py-1.5 bg-slate-950 hover:bg-slate-800 border border-slate-800 rounded-xl text-xs text-slate-300 font-medium transition"
                        >
                            👨‍🏫 1-Click Teacher
                        </button>
                    </div>
                </div>

                {/* Footer Toggle Text */}
                <div className="text-center text-xs text-slate-400">
                    {mode === 'LOGIN' ? (
                        <p>
                            Don't have an account?{' '}
                            <button
                                onClick={() => { setMode('SIGNUP'); setErrorMessage(''); }}
                                className="text-indigo-400 font-semibold hover:underline"
                            >
                                Sign up
                            </button>
                        </p>
                    ) : (
                        <p>
                            Already have an account?{' '}
                            <button
                                onClick={() => { setMode('LOGIN'); setErrorMessage(''); }}
                                className="text-indigo-400 font-semibold hover:underline"
                            >
                                Sign in
                            </button>
                        </p>
                    )}
                </div>

            </div>
        </div>
    );
};

export default AuthPage;