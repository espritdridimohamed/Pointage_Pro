export interface PayrollResponse {
  id: number;
  month: number;
  year: number;
  status: string;
  totalGross: number;
  totalDeductions: number;
  totalNet: number;
  employeeCount: number;
  createdAt: string;
  items: PayrollItemResponse[];
}

export interface PayrollItemResponse {
  id: number;
  employeeId: number;
  firstName: string;
  lastName: string;
  position: string;
  department: string;
  contractType?: string;
  photo?: string;
  initials: string;
  avatarColor: string;
  baseSalary: number;
  primeTransport: number;
  primePerformance: number;
  primeOther: number;
  overtimeHours: number;
  overtimeAmount: number;
  totalGross: number;
  cnssDeduction: number;
  assuranceDeduction: number;
  irDeduction: number;
  lateDeduction: number;
  absenceDeduction: number;
  missingHours: number;
  missingHoursDeduction: number;
  absenceHours: number;
  totalDeductions: number;
  netSalary: number;
  daysWorked: number;
  daysAbsent: number;
  lateMinutes: number;
  totalOvertimeMinutes: number;
  hourlyRate?: number;
  minuteRate?: number;
  status: string;
}

export interface PayrollItemUpdate {
  primeTransport?: number;
  primePerformance?: number;
  primeOther?: number;
}
