import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../core/auth/auth.service';
import { AuthService as AuthApiService, UserProfile, SessionResponse, LoginHistoryResponse, NotificationPrefs } from '../../core/services/auth-api.service';

@Component({
  selector: 'app-profile-view',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, MatButtonModule],
  templateUrl: './profile-view.component.html',
  styleUrl: './profile-view.component.scss'
})
export class ProfileViewComponent implements OnInit {
  activeTab = 0;
  loading = true;
  saving = false;
  changingPassword = false;

  tabs = [
    { label: 'Mon Profil', icon: 'person' },
    { label: 'Sécurité', icon: 'shield' },
    { label: 'Paramètres', icon: 'settings' },
    { label: 'Support', icon: 'help_outline' },
  ];

  profile = {
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    role: '',
    createdAt: '',
  };

  security = {
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
    twoFactorEnabled: false,
  };

  sessions: SessionResponse[] = [];
  loginHistory: LoginHistoryResponse[] = [];

  notifications: NotificationPrefs = {
    emailNotifications: true,
    browserNotifications: true,
    dailySummary: false,
  };

  supportForm = {
    name: '',
    email: '',
    subject: '',
    message: '',
  };

  successMessage = '';
  passwordSuccess = '';
  passwordError = '';

  twoFactorSetupMode = false;
  twoFactorSecret = '';
  twoFactorOtpauthUri = '';
  twoFactorCode = '';
  twoFactorLoading = false;
  twoFactorError = '';
  twoFactorSuccess = '';

  disable2FAMode = false;
  disable2FAPassword = '';
  disable2FACode = '';
  disable2FALoading = false;
  disable2FAError = '';

  sessionsLoading = false;
  historyLoading = false;
  prefsLoading = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private authApiService: AuthApiService,
    private authService: AuthService,
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      if (params['tab'] !== undefined) {
        this.activeTab = +params['tab'];
      }
    });
    this.loadProfile();
    this.loadSessions();
    this.loadLoginHistory();
    this.loadNotificationPrefs();
  }

  loadProfile(): void {
    this.loading = true;
    this.authApiService.getProfile().subscribe({
      next: (res) => {
        const data = res.data;
        const nameParts = (data.fullName || '').split(' ');
        this.profile.firstName = nameParts[0] || '';
        this.profile.lastName = nameParts.slice(1).join(' ') || '';
        this.profile.email = data.email || '';
        this.profile.phone = data.phone || '';
        this.profile.role = this.formatRole(data.role);
        this.profile.createdAt = '';
        this.supportForm.name = data.fullName || '';
        this.supportForm.email = data.email || '';
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  loadSessions(): void {
    this.sessionsLoading = true;
    this.authApiService.getSessions().subscribe({
      next: (res) => {
        this.sessions = res.data || [];
        this.sessionsLoading = false;
      },
      error: () => {
        this.sessionsLoading = false;
      }
    });
  }

  loadLoginHistory(): void {
    this.historyLoading = true;
    this.authApiService.getLoginHistory().subscribe({
      next: (res) => {
        this.loginHistory = res.data || [];
        this.historyLoading = false;
      },
      error: () => {
        this.historyLoading = false;
      }
    });
  }

  loadNotificationPrefs(): void {
    this.prefsLoading = true;
    this.authApiService.getNotificationPrefs().subscribe({
      next: (res) => {
        if (res.data) {
          this.notifications = res.data;
        }
        this.prefsLoading = false;
      },
      error: () => {
        this.prefsLoading = false;
      }
    });
  }

  setActiveTab(index: number): void {
    this.activeTab = index;
  }

  saveProfile(): void {
    this.saving = true;
    const fullName = `${this.profile.firstName} ${this.profile.lastName}`.trim();
    this.authApiService.updateProfile({
      fullName,
      email: this.profile.email,
      phone: this.profile.phone,
    }).subscribe({
      next: () => {
        this.saving = false;
        this.successMessage = 'Profil mis à jour avec succès';
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (err) => {
        this.saving = false;
        this.successMessage = err.error?.message || 'Erreur lors de la mise à jour du profil';
        setTimeout(() => this.successMessage = '', 3000);
      }
    });
  }

  changePassword(): void {
    this.passwordError = '';
    this.passwordSuccess = '';

    if (!this.security.currentPassword || !this.security.newPassword || !this.security.confirmPassword) {
      this.passwordError = 'Veuillez remplir tous les champs';
      return;
    }
    if (this.security.newPassword.length < 6) {
      this.passwordError = 'Le nouveau mot de passe doit contenir au moins 6 caractères';
      return;
    }
    if (this.security.newPassword !== this.security.confirmPassword) {
      this.passwordError = 'Les mots de passe ne correspondent pas';
      return;
    }

    this.changingPassword = true;
    this.authApiService.changePassword(this.security.currentPassword, this.security.newPassword).subscribe({
      next: (res) => {
        this.changingPassword = false;
        this.passwordSuccess = res.message || 'Mot de passe modifié avec succès';
        this.security.currentPassword = '';
        this.security.newPassword = '';
        this.security.confirmPassword = '';
        setTimeout(() => this.passwordSuccess = '', 3000);
      },
      error: (err) => {
        this.changingPassword = false;
        this.passwordError = err.error?.message || 'Erreur lors du changement de mot de passe';
      }
    });
  }

  setup2FA(): void {
    this.twoFactorLoading = true;
    this.twoFactorError = '';
    this.twoFactorSuccess = '';
    this.authApiService.setup2FA().subscribe({
      next: (res) => {
        this.twoFactorSecret = res.data.secret;
        this.twoFactorOtpauthUri = res.data.otpauthUri;
        this.twoFactorSetupMode = true;
        this.twoFactorLoading = false;
      },
      error: (err) => {
        this.twoFactorLoading = false;
        this.twoFactorError = err.error?.message || 'Erreur lors de l\'initialisation de la 2FA';
      }
    });
  }

  verify2FA(): void {
    if (!this.twoFactorCode || this.twoFactorCode.length !== 6) {
      this.twoFactorError = 'Veuillez entrer un code à 6 chiffres';
      return;
    }

    this.twoFactorLoading = true;
    this.twoFactorError = '';
    this.authApiService.verify2FA(this.twoFactorCode).subscribe({
      next: () => {
        this.authApiService.enable2FA().subscribe({
          next: () => {
            this.twoFactorLoading = false;
            this.security.twoFactorEnabled = true;
            this.twoFactorSetupMode = false;
            this.twoFactorSuccess = 'Authentification à deux facteurs activée avec succès';
            this.twoFactorCode = '';
            this.twoFactorSecret = '';
            this.twoFactorOtpauthUri = '';
            setTimeout(() => this.twoFactorSuccess = '', 3000);
          },
          error: (_err: unknown) => {
            this.twoFactorLoading = false;
            this.twoFactorError = 'Erreur lors de l\'activation';
          }
        });
      },
      error: (err) => {
        this.twoFactorLoading = false;
        this.twoFactorError = err.error?.message || 'Code invalide';
      }
    });
  }

  cancel2FASetup(): void {
    this.twoFactorSetupMode = false;
    this.twoFactorSecret = '';
    this.twoFactorOtpauthUri = '';
    this.twoFactorCode = '';
    this.twoFactorError = '';
  }

  startDisable2FA(): void {
    this.disable2FAMode = true;
    this.disable2FAPassword = '';
    this.disable2FACode = '';
    this.disable2FAError = '';
  }

  cancelDisable2FA(): void {
    this.disable2FAMode = false;
    this.disable2FAPassword = '';
    this.disable2FACode = '';
    this.disable2FAError = '';
  }

  disable2FA(): void {
    if (!this.disable2FAPassword || !this.disable2FACode) {
      this.disable2FAError = 'Veuillez remplir tous les champs';
      return;
    }

    this.disable2FALoading = true;
    this.disable2FAError = '';
    this.authApiService.disable2FA(this.disable2FAPassword, this.disable2FACode).subscribe({
      next: () => {
        this.disable2FALoading = false;
        this.security.twoFactorEnabled = false;
        this.disable2FAMode = false;
        this.disable2FAPassword = '';
        this.disable2FACode = '';
        this.successMessage = 'Authentification à deux facteurs désactivée';
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (err) => {
        this.disable2FALoading = false;
        this.disable2FAError = err.error?.message || 'Erreur lors de la désactivation de la 2FA';
      }
    });
  }

  revokeSession(id: number): void {
    const isCurrent = this.sessions.find(s => s.id === id)?.current;
    this.authApiService.revokeSession(id).subscribe({
      next: () => {
        this.sessions = this.sessions.filter(s => s.id !== id);
        if (isCurrent) {
          this.authService.logout();
        }
      },
      error: () => {}
    });
  }

  revokeAllSessions(): void {
    this.authApiService.revokeAllSessions().subscribe({
      next: () => {
        this.authService.logout();
      },
      error: (err) => {
        this.successMessage = err.error?.message || 'Erreur lors de la révocation';
        setTimeout(() => this.successMessage = '', 3000);
      }
    });
  }

  saveNotifications(): void {
    this.prefsLoading = true;
    this.authApiService.updateNotificationPrefs(this.notifications).subscribe({
      next: () => {
        this.prefsLoading = false;
        this.successMessage = 'Préférences de notification sauvegardées';
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (err) => {
        this.prefsLoading = false;
        this.successMessage = err.error?.message || 'Erreur lors de la sauvegarde';
        setTimeout(() => this.successMessage = '', 3000);
      }
    });
  }

  submitSupport(): void {
    if (!this.supportForm.subject || !this.supportForm.message) return;
    this.successMessage = 'Message envoyé avec succès ! Nous vous répondrons dans les plus brefs délais.';
    this.supportForm.subject = '';
    this.supportForm.message = '';
    setTimeout(() => this.successMessage = '', 4000);
  }

  getFullName(): string {
    return `${this.profile.firstName} ${this.profile.lastName}`.trim();
  }

  getInitials(): string {
    const f = this.profile.firstName?.[0] || '';
    const l = this.profile.lastName?.[0] || '';
    return `${f}${l}`.toUpperCase();
  }

  getQrCodeUrl(): string {
    return 'https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=' + encodeURIComponent(this.twoFactorOtpauthUri);
  }

  copySecret(): void {
    navigator.clipboard.writeText(this.twoFactorSecret);
    this.successMessage = 'Clé secrète copiée dans le presse-papiers';
    setTimeout(() => this.successMessage = '', 2000);
  }

  private formatRole(role: string): string {
    switch (role) {
      case 'ADMIN': return 'Administrateur';
      case 'HR': return 'Responsable RH';
      case 'MANAGER': return 'Manager';
      default: return role;
    }
  }
}
