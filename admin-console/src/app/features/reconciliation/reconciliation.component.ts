import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ReconciliationService } from '../../core/reconciliation.service';
import { ReconciliationResponse } from '../../core/models';

@Component({
  selector: 'app-reconciliation',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatChipsModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <div class="header">
      <h1>Reconciliation</h1>
      <button mat-stroked-button (click)="refresh()" [disabled]="loading()">
        <mat-icon>refresh</mat-icon>
        Refresh
      </button>
    </div>
    <p class="subtitle">
      The ledger's three money-conservation invariants, checked live against the current database --
      no locks taken, safe to run at any time.
    </p>

    @if (loading()) {
      <div class="loading"><mat-spinner diameter="32" /></div>
    } @else if (report()) {
      <div
        class="status-card"
        [class.healthy]="report()!.healthy"
        [class.unhealthy]="!report()!.healthy"
      >
        <mat-icon>{{ report()!.healthy ? 'check_circle' : 'error' }}</mat-icon>
        <span class="status-text">
          {{ report()!.healthy ? 'All invariants hold' : 'Invariant violation detected' }}
        </span>
      </div>

      <div class="grid">
        <mat-card>
          <mat-card-header><mat-card-title>Total balance</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="metric">{{ report()!.totalBalance | number: '1.2-2' }}</div>
          </mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-header><mat-card-title>Total opening balance</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="metric">{{ report()!.totalOpeningBalance | number: '1.2-2' }}</div>
          </mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-header><mat-card-title>Accounts with mismatched balance</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="metric" [class.bad]="report()!.accountsWithMismatchedBalance.length > 0">
              {{ report()!.accountsWithMismatchedBalance.length }}
            </div>
          </mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-header><mat-card-title>Transfers not netting to zero</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="metric" [class.bad]="report()!.transfersNotNettingToZero.length > 0">
              {{ report()!.transfersNotNettingToZero.length }}
            </div>
          </mat-card-content>
        </mat-card>
      </div>
    }
  `,
  styles: [
    `
      .header {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }
      .subtitle {
        color: rgba(0, 0, 0, 0.6);
        max-width: 640px;
      }
      .loading {
        display: flex;
        justify-content: center;
        padding: 48px;
      }
      .status-card {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 16px 20px;
        border-radius: 8px;
        margin-bottom: 20px;
        font-weight: 500;
      }
      .status-card.healthy {
        background: #e6f4ea;
        color: #1e7e34;
      }
      .status-card.unhealthy {
        background: #fce8e6;
        color: #b3261e;
      }
      .grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: 16px;
      }
      .metric {
        font-size: 1.8rem;
        font-weight: 600;
      }
      .metric.bad {
        color: #b3261e;
      }
    `,
  ],
})
export class ReconciliationComponent implements OnInit {
  readonly loading = signal(true);
  readonly report = signal<ReconciliationResponse | null>(null);

  constructor(private reconciliationService: ReconciliationService) {}

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.reconciliationService.check().subscribe({
      next: (r) => {
        this.report.set(r);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
