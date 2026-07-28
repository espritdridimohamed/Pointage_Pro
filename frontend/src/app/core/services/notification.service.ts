import { Injectable, Inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  constructor(@Inject(DOCUMENT) private document: Document) {}

  success(message: string, duration = 3000): void {
    this.show(message, 'success', duration);
  }

  error(message: string, duration = 4000): void {
    this.show(message, 'error', duration);
  }

  info(message: string, duration = 3000): void {
    this.show(message, 'info', duration);
  }

  private show(message: string, type: 'success' | 'error' | 'info', duration: number): void {
    const container = this.getContainer();
    const toast = this.document.createElement('div');
    toast.className = `app-toast app-toast-${type}`;
    toast.setAttribute('role', 'alert');

    const icon = this.getIcon(type);
    toast.innerHTML = `
      <span class="app-toast-icon">${icon}</span>
      <span class="app-toast-message">${this.escapeHtml(message)}</span>
    `;

    container.appendChild(toast);

    requestAnimationFrame(() => toast.classList.add('app-toast-visible'));

    setTimeout(() => {
      toast.classList.remove('app-toast-visible');
      setTimeout(() => toast.remove(), 300);
    }, duration);
  }

  private getContainer(): HTMLDivElement {
    let container = this.document.getElementById('app-toast-container') as HTMLDivElement;
    if (!container) {
      container = this.document.createElement('div');
      container.id = 'app-toast-container';
      this.document.body.appendChild(container);
    }
    return container;
  }

  private getIcon(type: string): string {
    switch (type) {
      case 'success': return '&#10003;';
      case 'error': return '&#10007;';
      case 'info': return '&#8505;';
      default: return '';
    }
  }

  private escapeHtml(text: string): string {
    const div = this.document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }
}
