// src/components/Navbar.tsx
import React from 'react';
import { useNavigate } from 'react-router-dom';
import { LogOut, GraduationCap, BookOpen } from 'lucide-react';

interface NavbarProps {
    user: { name: string; role: 'STUDENT' | 'TEACHER' } | null;
    onLogout: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({ user, onLogout }) => {
    const navigate = useNavigate();

    if (!user) return null; // Agar login nahi hai to navbar mat dikhao

    return (
        <nav className="bg-slate-900 border-b border-slate-800 px-4 sm:px-8 py-3.5 sticky top-0 z-40">
            <div className="max-w-7xl mx-auto flex items-center justify-between">

                {/* Brand Logo */}
                <div
                    onClick={() => navigate(user.role === 'TEACHER' ? '/teacher-dashboard' : '/student-dashboard')}
                    className="flex items-center gap-2.5 cursor-pointer"
                >
                    <div className="p-2 bg-indigo-600 rounded-xl text-white">
                        {user.role === 'TEACHER' ? <BookOpen size={20} /> : <GraduationCap size={20} />}
                    </div>
                    <div>
                        <span className="text-lg font-bold text-white tracking-tight">EduPortal</span>
                        <span className="hidden sm:inline-block ml-2 text-[11px] font-semibold px-2 py-0.5 rounded-full bg-slate-800 text-indigo-400 border border-slate-700 font-mono">
                            {user.role}
                        </span>
                    </div>
                </div>

                {/* User Info & Logout */}
                <div className="flex items-center gap-4">
                    <div className="flex items-center gap-2.5 text-sm text-slate-300">
                        <div className="w-8 h-8 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-indigo-400 font-bold">
                            {user.name.charAt(0)}
                        </div>
                        <span className="hidden sm:inline font-medium">{user.name}</span>
                    </div>

                    <button
                        onClick={onLogout}
                        className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 border border-rose-500/30 transition"
                    >
                        <LogOut size={14} />
                        <span>Logout</span>
                    </button>
                </div>

            </div>
        </nav>
    );
};