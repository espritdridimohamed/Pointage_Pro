import { Component, OnInit, OnDestroy, ViewChild, ElementRef, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatBadgeModule } from '@angular/material/badge';
import { AuthService } from '../../core/auth/auth.service';
import { AuthResponse } from '../../core/models/user.model';
import { NotificationApiService } from '../../core/services/notification-api.service';
import { AuthService as AuthApiService } from '../../core/services/auth-api.service';
import { Notification as AppNotification, getNotificationConfig } from '../../core/models/notification.model';
import { Subscription, filter, interval } from 'rxjs';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [
    CommonModule, RouterOutlet, RouterLink, RouterLinkActive,
    MatIconModule, MatBadgeModule
  ],
  templateUrl: './main-layout.component.html',
  styleUrl: './main-layout.component.scss'
})
export class MainLayoutComponent implements OnInit, OnDestroy {
  private userSub?: Subscription;
  private notifSub?: Subscription;
  private pollSub?: Subscription;
  private notifiedNotifIds = new Set<number>();
  private isFirstLoad = true;

  navItems = [
    { label: 'Dashboard', icon: 'dashboard', route: '/dashboard' },
    { label: 'Employés', icon: 'people_outline', route: '/employees' },
    { label: 'Pointage', icon: 'fact_check', route: '/attendance' },
    { label: 'Salaires', icon: 'payments', route: '/payroll' },
    { label: 'Congés', icon: 'event_busy', route: '/leaves' },
    { label: 'Rapports', icon: 'assessment', route: '/reports' },
    { label: 'Paramètres', icon: 'settings', route: '/settings' },
  ];

  breadcrumbMap: Record<string, string> = {
    '/dashboard': 'Dashboard',
    '/employees': 'Gestion des Employés',
    '/attendance': 'Pointage',
    '/payroll': 'Salaires',
    '/leaves': 'Congés',
    '/reports': 'Rapports',
    '/settings': 'Paramètres',
  };

  notifications: AppNotification[] = [];

  currentDate = '';
  userName = '';
  userFirstName = '';
  userLastName = '';
  userRole = '';
  userEmail = '';
  userInitials = '';
  breadcrumb = 'Dashboard';

  showNotifications = false;
  showProfile = false;

  browserNotifPermission: NotificationPermission = 'default';
  showNotifBanner = false;
  browserNotifEnabled = true;

  @ViewChild('pageContent') pageContent!: ElementRef<HTMLElement>;

  constructor(
    public authService: AuthService,
    private router: Router,
    private notifApi: NotificationApiService,
    private authApiService: AuthApiService
  ) {}

  ngOnInit(): void {
    this.updateDate();
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe(() => {
      this.updateBreadcrumb();
      requestAnimationFrame(() => {
        if (this.pageContent) {
          this.pageContent.nativeElement.scrollTop = 0;
        }
      });
    });
    this.updateBreadcrumb();

    this.userSub = this.authService.currentUser$.subscribe(user => {
      this.updateUser(user);
    });

    this.checkBrowserNotificationSupport();
    this.loadPreferences();
    this.loadNotifications();
    this.pollSub = interval(10000).subscribe(() => {
      this.loadNotifications();
      this.loadPreferences();
    });
  }

  ngOnDestroy(): void {
    this.userSub?.unsubscribe();
    this.notifSub?.unsubscribe();
    this.pollSub?.unsubscribe();
  }

  onLogout(): void {
    this.authApiService.revokeAllSessions().subscribe({
      next: () => this.authService.logout(),
      error: () => this.authService.logout()
    });
  }

  loadNotifications(): void {
    this.notifSub = this.notifApi.getAll(0, 20).subscribe({
      next: (res) => {
        const newNotifications = res.notifications.map(n => ({
          ...n,
          _config: getNotificationConfig(n.type),
          _timeAgo: this.timeAgo(n.createdAt)
        }));

        if (!this.isFirstLoad) {
          for (const notif of newNotifications) {
            if (!notif.read && !this.notifiedNotifIds.has(notif.id)) {
              this.notifiedNotifIds.add(notif.id);
              this.showBrowserNotification(notif.title, notif.message);
            }
          }
        } else {
          for (const notif of newNotifications) {
            this.notifiedNotifIds.add(notif.id);
          }
          this.isFirstLoad = false;
        }

        this.notifications = newNotifications;
      },
      error: () => {}
    });
  }

  checkBrowserNotificationSupport(): void {
    if ('Notification' in window) {
      this.browserNotifPermission = window.Notification.permission;
      if (this.browserNotifPermission === 'default') {
        this.showNotifBanner = true;
      }
    }
  }

  requestBrowserNotificationPermission(): void {
    if (!('Notification' in window)) return;

    window.Notification.requestPermission().then(permission => {
      this.browserNotifPermission = permission;
      this.showNotifBanner = false;
    });
  }

  dismissNotifBanner(): void {
    this.showNotifBanner = false;
    if ('Notification' in window) {
      window.Notification.requestPermission().then(permission => {
        this.browserNotifPermission = permission;
      });
    }
  }

  private showBrowserNotification(title: string, body: string): void {
    if (!this.browserNotifEnabled) return;
    if (!('Notification' in window)) return;
    if (window.Notification.permission !== 'granted') return;

    new window.Notification(title, {
      body,
      icon: 'https://ui-avatars.com/api/?name=PP&background=2563eb&color=fff&size=64',
      badge: 'https://ui-avatars.com/api/?name=PP&background=2563eb&color=fff&size=64',
      tag: 'pointagepro-' + Date.now(),
      requireInteraction: false,
    } as any);
  }

  loadPreferences(): void {
    this.authApiService.getNotificationPrefs().subscribe({
      next: (res) => {
        if (res.data) {
          this.browserNotifEnabled = res.data.browserNotifications;
        }
      },
      error: () => {}
    });
  }

  getNotifConfig(type: string) {
    return getNotificationConfig(type);
  }

  getNotifTimeAgo(createdAt: string): string {
    return this.timeAgo(createdAt);
  }

  markAsRead(event: MouseEvent, notif: any): void {
    event.stopPropagation();
    if (!notif.read) {
      this.notifApi.markAsRead(notif.id).subscribe(() => {
        notif.read = true;
      });
    }
  }

  markAllRead(): void {
    this.notifApi.markAllAsRead().subscribe(() => {
      this.notifications.forEach(n => n.read = true);
    });
  }

  get unreadCount(): number {
    return this.notifications.filter(n => !n.read).length;
  }

  private updateUser(user: AuthResponse | null): void {
    if (user) {
      this.userName = user.fullName || user.username || '';
      this.userEmail = user.email || '';
      this.userRole = this.formatRole(user.role);

      const parts = this.userName.split(' ');
      this.userFirstName = parts[0] || '';
      this.userLastName = parts.slice(1).join(' ') || '';

      const first = this.userFirstName?.[0] || '';
      const last = this.userLastName?.[0] || '';
      this.userInitials = `${first}${last}`.toUpperCase();
    } else {
      this.userName = '';
      this.userFirstName = '';
      this.userLastName = '';
      this.userRole = '';
      this.userEmail = '';
      this.userInitials = '';
    }
  }

  private formatRole(role: string): string {
    switch (role) {
      case 'ADMIN': return 'Administrateur';
      case 'HR': return 'Responsable RH';
      case 'MANAGER': return 'Manager';
      default: return role || '';
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (!target.closest('.notif-wrapper')) {
      this.showNotifications = false;
    }
    if (!target.closest('.profile-wrapper')) {
      this.showProfile = false;
    }
  }

  toggleNotifications(event: MouseEvent): void {
    event.stopPropagation();
    this.showNotifications = !this.showNotifications;
    this.showProfile = false;
    if (this.showNotifications) {
      this.loadNotifications();
    }
  }

  toggleProfile(event: MouseEvent): void {
    event.stopPropagation();
    this.showProfile = !this.showProfile;
    this.showNotifications = false;
  }

  private updateBreadcrumb(): void {
    const path = this.router.url;
    this.breadcrumb = this.breadcrumbMap[path] || 'Dashboard';
  }

  private updateDate(): void {
    const now = new Date();
    const options: Intl.DateTimeFormatOptions = { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' };
    this.currentDate = now.toLocaleDateString('fr-FR', options);
    this.currentDate = this.currentDate.charAt(0).toUpperCase() + this.currentDate.slice(1);
  }

  private timeAgo(dateStr: string): string {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    const now = new Date();
    const seconds = Math.floor((now.getTime() - date.getTime()) / 1000);

    if (seconds < 60) return "À l'instant";
    if (seconds < 3600) return `Il y a ${Math.floor(seconds / 60)} min`;
    if (seconds < 86400) return `Il y a ${Math.floor(seconds / 3600)}h`;
    if (seconds < 604800) return `Il y a ${Math.floor(seconds / 86400)}j`;
    return date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' });
  }
}
