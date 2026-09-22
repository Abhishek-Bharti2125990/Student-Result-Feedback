import React from 'react';
import { useNavigate } from 'react-router-dom';
import { GraduationCap, BookOpen, ArrowRight } from 'lucide-react';
import { useAppSelector } from '../redux/hooks';

export const Home: React.FC = () => {
    const navigate = useNavigate();
    const { user } = useAppSelector((state) => state.auth);

    const handleGo = (role: 'STUDENT' | 'TEACHER') => {
        if (!user) {
            navigate('/login');
            return;
        }
        navigate(user.role === 'TEACHER' ? '/teacher-dashboard' : '/student-dashboard');
    };

    return (
        <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col items-center justify-center p-4 sm:p-6">

            {/* Centered Main Box */}
            <div className="w-full max-w-2xl text-center space-y-8">

                {/* Title */}
                <div className="space-y-2">
                    <span className="text-xs font-semibold uppercase tracking-wider text-indigo-400 bg-indigo-500/10 px-3 py-1 rounded-md border border-indigo-500/20">
                        University Portal
                    </span>
                    <h1 className="text-3xl sm:text-5xl font-extrabold text-white tracking-tight">
                        Academic ERP System
                    </h1>
                    <p className="text-sm text-slate-400 max-w-md mx-auto">
                        View marks, check AI feedback, submit queries, and manage grades in one place.
                    </p>
                </div>

                {/* 2 Simple Cards: Student vs Teacher */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-left">

                    {/* Student Card */}
                    <div
                        onClick={() => handleGo('STUDENT')}
                        className="bg-slate-900 border border-slate-800 hover:border-indigo-500/50 p-6 rounded-lg cursor-pointer transition group"
                    >
                        <div className="w-10 h-10 rounded-md bg-indigo-500/10 text-indigo-400 flex items-center justify-center mb-4">
                            <GraduationCap size={20} />
                        </div>
                        <h2 className="text-base font-bold text-white group-hover:text-indigo-400 transition">
                            Student Portal
                        </h2>
                        <p className="text-xs text-slate-400 mt-1">
                            Check all semester results, graph trends, and ask faculty doubts.
                        </p>
                        <div className="mt-4 flex items-center gap-1.5 text-xs font-semibold text-indigo-400">
                            <span>Open Portal</span>
                            <ArrowRight size={13} className="group-hover:translate-x-1 transition-transform" />
                        </div>
                    </div>

                    {/* Teacher Card */}
                    <div
                        onClick={() => handleGo('TEACHER')}
                        className="bg-slate-900 border border-slate-800 hover:border-indigo-500/50 p-6 rounded-lg cursor-pointer transition group"
                    >
                        <div className="w-10 h-10 rounded-md bg-indigo-500/10 text-indigo-400 flex items-center justify-center mb-4">
                            <BookOpen size={20} />
                        </div>
                        <h2 className="text-base font-bold text-white group-hover:text-indigo-400 transition">
                            Faculty Portal
                        </h2>
                        <p className="text-xs text-slate-400 mt-1">
                            Add/edit student results (CRUD) and respond to academic queries.
                        </p>
                        <div className="mt-4 flex items-center gap-1.5 text-xs font-semibold text-indigo-400">
                            <span>Open Console</span>
                            <ArrowRight size={13} className="group-hover:translate-x-1 transition-transform" />
                        </div>
                    </div>

                </div>

            </div>

        </div>
    );
};

export default Home;