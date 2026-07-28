export interface AttendanceRecord {
  id: number;
  employeeId: number;
  date: string;
  checkIn: string | null;
  checkOut: string | null;
  workedHours: number;
  lateMinutes: number;
  overtimeHours: number;
  status: string;
}

export interface AttendanceSummary {
  employeeId: number;
  month: number;
  year: number;
  overtimeHours: number;
  lateMinutes: number;
  daysWorked: number;
  daysAbsent: number;
  totalWorkDays: number;
}
