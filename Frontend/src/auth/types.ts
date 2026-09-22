export type Role = 'STUDENT' | 'TEACHER';

export interface AuthUser {
    name: string;
    email: string;
    role: Role;
    rollNo?: string;
    semester?: string;
    department?: string;
    employeeId?: string;
}