import { Component, ViewChildren, QueryList, ElementRef, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule, RouterLink
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  username = '';
  password = '';
  hidePassword = true;
  loading = false;
  errorMessage = '';

  twoFactorMode = false;
  twoFactorCode = ['', '', '', '', '', ''];
  twoFactorLoading = false;
  twoFactorError = '';
  private tempToken = '';

  @ViewChildren('codeInput') codeInputs!: QueryList<ElementRef>;

  constructor(private authService: AuthService, private router: Router) {}

  onSubmit(): void {
    if (!this.username || !this.password) return;

    this.loading = true;
    this.errorMessage = '';

    this.authService.login({ username: this.username, password: this.password }).subscribe({
      next: (res) => {
        if (this.authService['twoFactorRequiredSubject'].value) {
          this.tempToken = this.authService['tempTokenSubject'].value || '';
          this.twoFactorMode = true;
          this.loading = false;
          setTimeout(() => {
            const inputs = this.codeInputs?.toArray();
            if (inputs && inputs.length > 0) {
              inputs[0].nativeElement.focus();
            }
          });
        } else {
          this.router.navigate(['/dashboard']);
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage = err.error?.message || 'Invalid credentials';
      }
    });
  }

  onCodeInput(index: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = input.value;

    if (value.length === 1) {
      this.twoFactorCode[index] = value;
      if (index < 5) {
        const inputs = this.codeInputs?.toArray();
        if (inputs && inputs[index + 1]) {
          inputs[index + 1].nativeElement.focus();
        }
      }
    }
  }

  onCodeKeydown(index: number, event: KeyboardEvent): void {
    if (event.key === 'Backspace') {
      if (!this.twoFactorCode[index] && index > 0) {
        const inputs = this.codeInputs?.toArray();
        if (inputs && inputs[index - 1]) {
          inputs[index - 1].nativeElement.focus();
        }
      }
      this.twoFactorCode[index] = '';
    }
  }

  onCodePaste(event: ClipboardEvent): void {
    event.preventDefault();
    const pasted = event.clipboardData?.getData('text') || '';
    const digits = pasted.replace(/\D/g, '').slice(0, 6).split('');
    const inputs = this.codeInputs?.toArray();

    for (let i = 0; i < 6; i++) {
      this.twoFactorCode[i] = digits[i] || '';
      if (inputs && inputs[i] && digits[i]) {
        inputs[i].nativeElement.value = digits[i];
      }
    }

    const nextEmpty = this.twoFactorCode.findIndex(c => !c);
    const focusIndex = nextEmpty === -1 ? 5 : nextEmpty;
    if (inputs && inputs[focusIndex]) {
      inputs[focusIndex].nativeElement.focus();
    }
  }

  isCodeComplete(): boolean {
    return this.twoFactorCode.every(c => c.length === 1);
  }

  onSubmit2FA(): void {
    if (!this.isCodeComplete()) return;

    this.twoFactorLoading = true;
    this.twoFactorError = '';

    const code = this.twoFactorCode.join('');

    this.authService.login2FA(this.tempToken, code).subscribe({
      next: () => {
        this.twoFactorLoading = false;
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.twoFactorLoading = false;
        this.twoFactorError = err.error?.message || 'Code invalide';
        this.twoFactorCode = ['', '', '', '', '', ''];
        const inputs = this.codeInputs?.toArray();
        if (inputs && inputs.length > 0) {
          inputs[0].nativeElement.focus();
          inputs.forEach(i => i.nativeElement.value = '');
        }
      }
    });
  }

  cancel2FA(): void {
    this.twoFactorMode = false;
    this.twoFactorCode = ['', '', '', '', '', ''];
    this.twoFactorError = '';
    this.authService.clear2FAState();
  }
}
