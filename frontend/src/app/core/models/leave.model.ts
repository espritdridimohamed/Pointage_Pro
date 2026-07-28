export interface LeaveRequest {
  id: number;
  employeeId: number;
  firstName: string;
  lastName: string;
  initials: string;
  avatarColor: string;
  photo?: string;
  leaveType: string;
  startDate: string;
  endDate: string;
  days: number;
  reason?: string;
  hasAttachment: boolean;
  attachment?: string;
  status: 'Approuvé' | 'En cours' | 'Refusé';
  requestedDate: string;
  approvedByName?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface LeaveRequestCreate {
  employeeId: number;
  leaveType: string;
  startDate: string;
  endDate: string;
  reason?: string;
  attachment?: string;
}

export interface LeaveBalance {
  type: string;
  total: number | null;
  used: number;
  remaining: number | null;
  color: string;
}
