export interface Notification {
  id: number;
  type: string;
  title: string;
  message: string;
  priority: string;
  relatedEntityType: string | null;
  relatedEntityId: number | null;
  read: boolean;
  createdAt: string;
}

export interface NotificationTypeConfig {
  icon: string;
  iconColor: string;
  iconBg: string;
  label: string;
}

export const NOTIFICATION_TYPE_MAP: Record<string, NotificationTypeConfig> = {
  CHECK_IN:              { icon: 'login',                iconColor: '#22c55e', iconBg: '#f0fdf4', label: 'Arrivée' },
  CHECK_OUT:             { icon: 'logout',              iconColor: '#3b82f6', iconBg: '#eff6ff', label: 'Départ' },
  LATE_ARRIVAL:          { icon: 'schedule',            iconColor: '#f59e0b', iconBg: '#fffbeb', label: 'Retard' },
  EARLY_DEPARTURE:       { icon: 'departure_board',     iconColor: '#f97316', iconBg: '#fff7ed', label: 'Départ anticipé' },
  INCOMPLETE_SCAN:       { icon: 'report_problem',      iconColor: '#ef4444', iconBg: '#fef2f2', label: 'Pointage incomplet' },
  LEAVE_REQUEST:         { icon: 'event_busy',          iconColor: '#8b5cf6', iconBg: '#f5f3ff', label: 'Demande congé' },
  LEAVE_APPROVED_INFO:   { icon: 'check_circle',        iconColor: '#22c55e', iconBg: '#f0fdf4', label: 'Congé approuvé' },
  LEAVE_REFUSED:         { icon: 'cancel',              iconColor: '#ef4444', iconBg: '#fef2f2', label: 'Congé refusé' },
  LEAVE_LOW_BALANCE:     { icon: 'warning',             iconColor: '#f59e0b', iconBg: '#fffbeb', label: 'Solde congé bas' },
  LEAVE_ENDED:           { icon: 'replay',              iconColor: '#06b6d4', iconBg: '#ecfeff', label: 'Retour congé' },
  PAYROLL_GENERATED:     { icon: 'receipt_long',        iconColor: '#22c55e', iconBg: '#f0fdf4', label: 'Paie générée' },
  PAYROLL_ITEM_PAID:     { icon: 'paid',                iconColor: '#3b82f6', iconBg: '#eff6ff', label: 'Salaire versé' },
  PAYROLL_ALL_PAID:      { icon: 'task_alt',            iconColor: '#22c55e', iconBg: '#f0fdf4', label: 'Paie finalisée' },
  PAYROLL_ANOMALY:       { icon: 'error_outline',       iconColor: '#ef4444', iconBg: '#fef2f2', label: 'Anomalie paie' },
  EMPLOYEE_CREATED:      { icon: 'person_add',          iconColor: '#22c55e', iconBg: '#f0fdf4', label: 'Nouvel employé' },
  EMPLOYEE_STATUS_CHANGE:{ icon: 'swap_horiz',          iconColor: '#f59e0b', iconBg: '#fffbeb', label: 'Changement statut' },
  EMPLOYEE_RFID_ASSIGNED:{ icon: 'credit_card',         iconColor: '#06b6d4', iconBg: '#ecfeff', label: 'Badge assigné' },
  TERMINAL_OFFLINE:      { icon: 'wifi_off',            iconColor: '#ef4444', iconBg: '#fef2f2', label: 'Terminal hors ligne' },
  TERMINAL_ONLINE:       { icon: 'wifi',                iconColor: '#22c55e', iconBg: '#f0fdf4', label: 'Terminal reconnecté' },
  UNKNOWN_BADGE:         { icon: 'badge',               iconColor: '#ef4444', iconBg: '#fef2f2', label: 'Badge inconnu' },
  INACTIVE_SCAN:         { icon: 'block',               iconColor: '#ef4444', iconBg: '#fef2f2', label: 'Badge désactivé' },
  DAILY_SUMMARY:         { icon: 'summarize',           iconColor: '#3b82f6', iconBg: '#eff6ff', label: 'Résumé du jour' },
  WEEKLY_SUMMARY:        { icon: 'calendar_view_week',  iconColor: '#8b5cf6', iconBg: '#f5f3ff', label: 'Résumé hebdo' },
  MONTHLY_SUMMARY:       { icon: 'calendar_month',      iconColor: '#06b6d4', iconBg: '#ecfeff', label: 'Résumé mensuel' },
  SETTINGS_CHANGED:      { icon: 'settings',            iconColor: '#64748b', iconBg: '#f1f5f9', label: 'Paramètres modifiés' },
  AUTO_RESTORE:          { icon: 'restore',             iconColor: '#06b6d4', iconBg: '#ecfeff', label: 'Congé expiré' },
};

export function getNotificationConfig(type: string): NotificationTypeConfig {
  return NOTIFICATION_TYPE_MAP[type] || { icon: 'notifications', iconColor: '#64748b', iconBg: '#f1f5f9', label: 'Notification' };
}
