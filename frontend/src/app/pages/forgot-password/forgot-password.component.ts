import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../core/services/auth-api.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, RouterLink],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.scss'
})
export class ForgotPasswordComponent {
  step = 1;
  email = '';
  code = ['', '', '', '', '', ''];
  newPassword = '';
  confirmPassword = '';
  loading = false;
  errorMessage = '';
  successMessage = '';

  constructor(private authApi: AuthService, private router: Router) {}

  sendCode(): void {
    if (!this.email) return;
    this.loading = true;
    this.errorMessage = '';

    this.authApi.forgotPassword(this.email).subscribe({
      next: () => {
        this.loading = false;
        this.step = 2;
        this.successMessage = 'Un code a été envoyé à votre adresse email';
        setTimeout(() => this.successMessage = '', 4000);
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Erreur lors de l\'envoi';
      }
    });
  }

  verifyCode(): void {
    const codeStr = this.code.join('');
    if (codeStr.length !== 6) return;
    this.loading = true;
    this.errorMessage = '';

    this.authApi.verifyResetCode(this.email, codeStr).subscribe({
      next: () => {
        this.loading = false;
        this.step = 3;
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Code invalide ou expiré';
        this.code = ['', '', '', '', '', ''];
      }
    });
  }

  resetPassword(): void {
    const codeStr = this.code.join('');
    if (!this.newPassword || !this.confirmPassword) return;

    if (this.newPassword.length < 6) {
      this.errorMessage = 'Le mot de passe doit contenir au moins 6 caractères';
      return;
    }
    if (this.newPassword !== this.confirmPassword) {
      this.errorMessage = 'Les mots de passe ne correspondent pas';
      return;
    }

    this.loading = true;
    this.errorMessage = '';

    this.authApi.resetPassword(this.email, codeStr, this.newPassword).subscribe({
      next: () => {
        this.loading = false;
        this.successMessage = 'Mot de passe réinitialisé avec succès ! Redirection...';
        setTimeout(() => this.router.navigate(['/login']), 2000);
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Erreur lors de la réinitialisation';
      }
    });
  }

  onCodeInput(index: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = input.value;
    if (value.length === 1 && /^\d$/.test(value)) {
      this.code[index] = value;
      if (index < 5) {
        const next = document.querySelectorAll('.code-input')[index + 1] as HTMLInputElement;
        if (next) next.focus();
      }
    }
  }

  onCodeKeydown(index: number, event: KeyboardEvent): void {
    if (event.key === 'Backspace') {
      if (!this.code[index] && index > 0) {
        const prev = document.querySelectorAll('.code-input')[index - 1] as HTMLInputElement;
        if (prev) prev.focus();
      }
      this.code[index] = '';
    }
  }

  onCodePaste(event: ClipboardEvent): void {
    event.preventDefault();
    const pasted = event.clipboardData?.getData('text') || '';
    const digits = pasted.replace(/\D/g, '').slice(0, 6).split('');
    const inputs = document.querySelectorAll('.code-input');
    for (let i = 0; i < 6; i++) {
      this.code[i] = digits[i] || '';
      if (inputs[i] && digits[i]) {
        (inputs[i] as HTMLInputElement).value = digits[i];
      }
    }
    const nextEmpty = this.code.findIndex(c => !c);
    const focusIdx = nextEmpty === -1 ? 5 : nextEmpty;
    if (inputs[focusIdx]) (inputs[focusIdx] as HTMLInputElement).focus();
  }

  isCodeComplete(): boolean {
    return this.code.every(c => c.length === 1);
  }
}
