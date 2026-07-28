export interface Employee {
  id: number;
  matricule: string;
  firstName: string;
  lastName: string;
  phone?: string;
  email?: string;
  position?: string;
  department?: string;
  contractType?: string;
  photo?: string;
  birthDate?: string;
  cin?: string;
  address?: string;
  baseSalary?: number;
  primeTransport?: number;
  primePerformance?: number;
  primeOther?: number;
  totalPrimes?: number;
  rfidUid?: string;
  weeklySchedule?: string;
  annualLeaveDays?: number;
  maternityLeaveDays?: number;
  paternityLeaveDays?: number;
  hiringDate?: string;
  status: 'ACTIF' | 'INACTIF' | 'CONGE';
  createdAt?: string;
  updatedAt?: string;
}

export interface EmployeeRequest {
  matricule?: string;
  firstName: string;
  lastName: string;
  phone?: string;
  email?: string;
  position?: string;
  department?: string;
  contractType?: string;
  photo?: string;
  birthDate?: string;
  cin?: string;
  address?: string;
  baseSalary?: number;
  primeTransport?: number;
  primePerformance?: number;
  primeOther?: number;
  rfidUid?: string;
  weeklySchedule?: string;
  annualLeaveDays?: number;
  maternityLeaveDays?: number;
  paternityLeaveDays?: number;
  hiringDate?: string;
  status: string;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
