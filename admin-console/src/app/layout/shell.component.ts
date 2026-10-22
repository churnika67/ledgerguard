import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatButtonModule, MatIconModule],
  template: `
    <mat-toolbar color="primary" class="toolbar">
      <span class="brand">
        <mat-icon>shield</mat-icon>
        LedgerGuard Admin
      </span>
      <nav class="links">
        <a mat-button routerLink="/reconciliation" routerLinkActive="active">
          <mat-icon>fact_check</mat-icon> Reconciliation
        </a>
        <a mat-button routerLink="/accounts" routerLinkActive="active">
          <mat-icon>account_balance_wallet</mat-icon> Accounts
        </a>
        <a mat-button routerLink="/transfers" routerLinkActive="active">
          <mat-icon>receipt_long</mat-icon> Transfers
        </a>
        <a mat-button routerLink="/chaos" routerLinkActive="active">
          <mat-icon>bolt</mat-icon> Chaos Test
        </a>
      </nav>
      <span class="spacer"></span>
      <span class="username">{{ auth.username() }}</span>
      <button mat-icon-button (click)="logout()" aria-label="Log out">
        <mat-icon>logout</mat-icon>
      </button>
    </mat-toolbar>
    <main class="content">
      <router-outlet />
    </main>
  `,
  styles: [
    `
      .toolbar {
        display: flex;
        gap: 8px;
        position: sticky;
        top: 0;
        z-index: 10;
      }
      .brand {
        display: flex;
        align-items: center;
        gap: 8px;
        font-weight: 600;
        margin-right: 24px;
      }
      .links {
        display: flex;
        gap: 4px;
      }
      .links a {
        display: flex;
        align-items: center;
        gap: 4px;
      }
      .links a.active {
        background: rgba(255, 255, 255, 0.15);
      }
      .spacer {
        flex: 1 1 auto;
      }
      .username {
        margin-right: 4px;
        opacity: 0.85;
        font-size: 0.9rem;
      }
      .content {
        max-width: 1200px;
        margin: 0 auto;
        padding: 24px 16px 48px;
      }
    `,
  ],
})
export class ShellComponent {
  constructor(
    public auth: AuthService,
    private router: Router,
  ) {}

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
