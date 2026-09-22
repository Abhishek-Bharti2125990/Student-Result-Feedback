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
    AlertCircle
} from 'lucide-react';
import type { Role, AuthUser } from '../types';

interface SignUpProps {
    onLoginSuccess: (user: AuthUser) => void;
    onSwitchToSignIn: () => void;
    currentRole: Role;
    onRoleChange: (role: Role) => void;
}

export const SignUp: React.FC<SignUpProps> = ({
    onLoginSuccess,
    onSwitchToSignIn,
    currentRole,
    onRoleChange,
}) => {
    const [formData, setFormData] = useState({
        name: '',
        email: '',
        password: '',
        rollNo: '',
        semester: '1',
        department: '',
        employeeId: ''
    });

    const [showPassword, setShowPassword] = useState(false);
    const [loading, setLoading] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');

    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        const { name, value } = e.target;
        setErrorMessage('');
        setFormData((prev) => ({ ...prev, [name]: value }));
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setErrorMessage('');
        setLoading(true);

        await new Promise((resolve) => setTimeout(resolve, 500));

        const storedUsersRaw = localStorage.getItem('registered_users');
        const registeredUsers: any[] = storedUsersRaw ? JSON.parse(storedUsersRaw) : [];

        const userExists = registeredUsers.some(
            (u) => u.email.toLowerCase() === formData.email.toLowerCase()
        );

        if (userExists) {
            setErrorMessage('User with this email already exists. Please sign in.');
            setLoading(false);
            return;
        }

        const newUser = {
            name: formData.name,
            email: formData.email,
            password: formData.password,
            role: currentRole,
            rollNo: formData.rollNo,
            semester: formData.semester,
            department: formData.department,
            employeeId: formData.employeeId
        };

        registeredUsers.push(newUser);
        localStorage.setItem('registered_users', JSON.stringify(registeredUsers));

        const { password: _, ...safeUser } = newUser;
        onLoginSuccess(safeUser as AuthUser);
        setLoading(false);
    };

    return (
        <div className="space-y-4">
            {errorMessage && (
                <div className="p-2.5 rounded-md bg-rose-500/10 border border-rose-500/25 text-rose-400 text-xs flex items-center gap-2">
                    <AlertCircle size={15} className="shrink-0" />
                    <span>{errorMessage}</span>
                </div>
            )}

            {/* Role Switcher */}
            <div>
                <label className="block text-[11px] font-semibold text-slate-400 uppercase tracking-wider mb-1.5">
                    Select Identity Role
                </label>
                <div className="grid grid-cols-2 gap-2">
                    <button
                        type="button"
                        onClick={() => onRoleChange('STUDENT')}
                        className={`flex items-center justify-center gap-2 p-2 rounded-md border text-xs font-medium cursor-pointer transition ${currentRole === 'STUDENT'
                                ? 'border-indigo-500 bg-indigo-500/10 text-indigo-400'
                                : 'border-slate-800 bg-slate-950/60 text-slate-400 hover:border-slate-700'
                            }`}
                    >
                        <GraduationCap size={15} />
                        <span>Student</span>
                    </button>

                    <button
                        type="button"
                        onClick={() => onRoleChange('TEACHER')}
                        className={`flex items-center justify-center gap-2 p-2 rounded-md border text-xs font-medium cursor-pointer transition ${currentRole === 'TEACHER'
                                ? 'border-indigo-500 bg-indigo-500/10 text-indigo-400'
                                : 'border-slate-800 bg-slate-950/60 text-slate-400 hover:border-slate-700'
                            }`}
                    >
                        <BookOpen size={15} />
                        <span>Teacher</span>
                    </button>
                </div>
            </div>

            <form onSubmit={handleSubmit} className="space-y-3">
                <div>
                    <label className="block text-xs font-medium text-slate-300 mb-1">Full Name</label>
                    <div className="relative">
                        <User size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none" />
                        <input
                            type="text"
                            name="name"
                            required
                            placeholder="e.g. Rahul Sharma"
                            value={formData.name}
                            onChange={handleInputChange}
                            className="w-full bg-slate-950/80 border border-slate-700/80 rounded-md pl-9 pr-3 py-1.5 text-xs sm:text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition"
                        />
                    </div>
                </div>

                <div>
                    <label className="block text-xs font-medium text-slate-300 mb-1">Email Address</label>
                    <div className="relative">
                        <Mail size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none" />
                        <input
                            type="email"
                            name="email"
                            required
                            placeholder="name@university.edu"
                            value={formData.email}
                            onChange={handleInputChange}
                            className="w-full bg-slate-950/80 border border-slate-700/80 rounded-md pl-9 pr-3 py-1.5 text-xs sm:text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition"
                        />
                    </div>
                </div>

                {currentRole === 'STUDENT' ? (
                    <div className="grid grid-cols-2 gap-2">
                        <div>
                            <label className="block text-xs font-medium text-slate-300 mb-1">Roll Number</label>
                            <div className="relative">
                                <IdCard size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none" />
                                <input
                                    type="text"
                                    name="rollNo"
                                    required
                                    placeholder="21CS042"
                                    value={formData.rollNo}
                                    onChange={handleInputChange}
                                    className="w-full bg-slate-950/80 border border-slate-700/80 rounded-md pl-9 pr-2 py-1.5 text-xs sm:text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500"
                                />
                            </div>
                        </div>
                        <div>
                            <label className="block text-xs font-medium text-slate-300 mb-1">Semester</label>
                            <select
                                name="semester"
                                value={formData.semester}
                                onChange={handleInputChange}
                                className="w-full bg-slate-950/80 border border-slate-700/80 rounded-md px-2 py-1.5 text-xs sm:text-sm text-slate-100 focus:outline-none focus:border-indigo-500 cursor-pointer"
                            >
                                {[1, 2, 3, 4, 5, 6, 7, 8].map((sem) => (
                                    <option key={sem} value={sem} className="bg-slate-900 text-white">
                                        Semester 0{sem}
                                    </option>
                                ))}
                            </select>
                        </div>
                    </div>
                ) : (
                    <div className="grid grid-cols-2 gap-2">
                        <div>
                            <label className="block text-xs font-medium text-slate-300 mb-1">Department</label>
                            <div className="relative">
                                <Building size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none" />
                                <input
                                    type="text"
                                    name="department"
                                    required
                                    placeholder="CSE / IT"
                                    value={formData.department}
                                    onChange={handleInputChange}
                                    className="w-full bg-slate-950/80 border border-slate-700/80 rounded-md pl-9 pr-2 py-1.5 text-xs sm:text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500"
                                />
                            </div>
                        </div>
                        <div>
                            <label className="block text-xs font-medium text-slate-300 mb-1">Employee ID</label>
                            <div className="relative">
                                <IdCard size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none" />
                                <input
                                    type="text"
                                    name="employeeId"
                                    required
                                    placeholder="EMP-102"
                                    value={formData.employeeId}
                                    onChange={handleInputChange}
                                    className="w-full bg-slate-950/80 border border-slate-700/80 rounded-md pl-9 pr-2 py-1.5 text-xs sm:text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500"
                                />
                            </div>
                        </div>
                    </div>
                )}

                <div>
                    <label className="block text-xs font-medium text-slate-300 mb-1">Password</label>
                    <div className="relative">
                        <Lock size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none" />
                        <input
                            type={showPassword ? 'text' : 'password'}
                            name="password"
                            required
                            placeholder="••••••••"
                            value={formData.password}
                            onChange={handleInputChange}
                            className="w-full bg-slate-950/80 border border-slate-700/80 rounded-md pl-9 pr-9 py-1.5 text-xs sm:text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition"
                        />
                        <button
                            type="button"
                            onClick={() => setShowPassword(!showPassword)}
                            className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 cursor-pointer p-1 transition"
                        >
                            {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                        </button>
                    </div>
                </div>

                <button
                    type="submit"
                    disabled={loading}
                    className="w-full mt-1 flex items-center justify-center gap-2 bg-indigo-600 hover:bg-indigo-500 text-white font-medium py-2 rounded-md text-xs sm:text-sm shadow-sm cursor-pointer transition disabled:opacity-50 disabled:cursor-not-allowed"
                >
                    {loading ? (
                        <span className="inline-block w-4 h-4 border-2 border-white/20 border-t-white rounded-full animate-spin" />
                    ) : (
                        <>
                            <span>Create Account</span>
                            <ArrowRight size={14} />
                        </>
                    )}
                </button>
            </form>

            <div className="text-center text-xs text-slate-400 pt-1">
                Already have an account?{' '}
                <button
                    type="button"
                    onClick={onSwitchToSignIn}
                    className="text-indigo-400 font-semibold hover:underline cursor-pointer"
                >
                    Sign in
                </button>
            </div>
        </div>
    );
};

export default SignUp;