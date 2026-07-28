import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [MatButtonModule, RouterLink],
  template: `
    <div class="not-found">
      <h1>404</h1>
      <p>Page not found</p>
      <a mat-raised-button color="primary" routerLink="/dashboard">Go to Dashboard</a>
    </div>
  `,
  styles: [`
    .not-found {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 100vh;
      text-align: center;
    }
    h1 { font-size: 72px; color: #3f51b5; margin: 0; }
    p { font-size: 18px; color: #666; margin: 16px 0 32px; }
  `]
})
export class NotFoundComponent {}
