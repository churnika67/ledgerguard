import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TransfersService } from '../../core/transfers.service';
import { RejectReason, TransferResponse, TransferStatus } from '../../core/models';

@Component({
  selector: 'app-transfers',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatSelectModule,
    MatChipsModule,
    MatProgressBarModule,
  ],
  template: `
    <h1>Transfer audit log</h1>
    <p class="subtitle">Every transfer ever attempted, filterable by outcome.</p>

    <div class="filters">
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="status">
          <mat-option [value]="null">All</mat-option>
          <mat-option value="COMPLETED">Completed</mat-option>
          <mat-option value="REJECTED">Rejected</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Reject reason</mat-label>
        <mat-select [formControl]="rejectReason">
          <mat-option [value]="null">All</mat-option>
          <mat-option value="INSUFFICIENT_FUNDS">Insufficient funds</mat-option>
          <mat-option value="SAME_ACCOUNT">Same account</mat-option>
          <mat-option value="CURRENCY_MISMATCH">Currency mismatch</mat-option>
        </mat-select>
      </mat-form-field>
    </div>

    @if (loading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    <table mat-table [dataSource]="rows()" class="mat-elevation-z1 table">
      <ng-container matColumnDef="transferId">
        <th mat-header-cell *matHeaderCellDef>Transfer</th>
        <td mat-cell *matCellDef="let t"><code>{{ t.transferId.slice(0, 8) }}</code></td>
      </ng-container>
      <ng-container matColumnDef="fromAccountId">
        <th mat-header-cell *matHeaderCellDef>From</th>
        <td mat-cell *matCellDef="let t"><code>{{ t.fromAccountId.slice(0, 8) }}</code></td>
      </ng-container>
      <ng-container matColumnDef="toAccountId">
        <th mat-header-cell *matHeaderCellDef>To</th>
        <td mat-cell *matCellDef="let t"><code>{{ t.toAccountId.slice(0, 8) }}</code></td>
      </ng-container>
      <ng-container matColumnDef="amount">
        <th mat-header-cell *matHeaderCellDef>Amount</th>
        <td mat-cell *matCellDef="let t">{{ t.amount | number: '1.2-2' }} {{ t.currency }}</td>
      </ng-container>
      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef>Status</th>
        <td mat-cell *matCellDef="let t">
          <mat-chip [class.completed]="t.status === 'COMPLETED'" [class.rejected]="t.status === 'REJECTED'">
            {{ t.status }}
          </mat-chip>
        </td>
      </ng-container>
      <ng-container matColumnDef="rejectReason">
        <th mat-header-cell *matHeaderCellDef>Reject reason</th>
        <td mat-cell *matCellDef="let t">{{ t.rejectReason ?? '--' }}</td>
      </ng-container>
      <ng-container matColumnDef="createdAt">
        <th mat-header-cell *matHeaderCellDef>When</th>
        <td mat-cell *matCellDef="let t">{{ t.createdAt | date: 'short' }}</td>
      </ng-container>

      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns"></tr>
    </table>

    @if (!loading() && rows().length === 0) {
      <p class="empty">No transfers match.</p>
    }

    <mat-paginator
      [length]="totalElements()"
      [pageSize]="pageSize"
      [pageSizeOptions]="[10, 20, 50]"
      (page)="onPage($event)"
    />
  `,
  styles: [
    `
      .subtitle {
        color: rgba(0, 0, 0, 0.6);
      }
      .filters {
        display: flex;
        gap: 16px;
        flex-wrap: wrap;
      }
      .table {
        width: 100%;
      }
      .empty {
        padding: 24px;
        text-align: center;
        color: rgba(0, 0, 0, 0.5);
      }
      mat-chip.completed {
        --mdc-chip-elevated-container-color: #e6f4ea;
        color: #1e7e34;
      }
      mat-chip.rejected {
        --mdc-chip-elevated-container-color: #fce8e6;
        color: #b3261e;
      }
    `,
  ],
})
export class TransfersComponent implements OnInit {
  readonly columns = ['transferId', 'fromAccountId', 'toAccountId', 'amount', 'status', 'rejectReason', 'createdAt'];
  readonly rows = signal<TransferResponse[]>([]);
  readonly totalElements = signal(0);
  readonly loading = signal(false);
  readonly status = new FormControl<TransferStatus | null>(null);
  readonly rejectReason = new FormControl<RejectReason | null>(null);

  pageSize = 10;
  private pageIndex = 0;

  constructor(private transfersService: TransfersService) {}

  ngOnInit(): void {
    this.fetch();
    this.status.valueChanges.subscribe(() => {
      this.pageIndex = 0;
      this.fetch();
    });
    this.rejectReason.valueChanges.subscribe(() => {
      this.pageIndex = 0;
      this.fetch();
    });
  }

  onPage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.fetch();
  }

  private fetch(): void {
    this.loading.set(true);
    this.transfersService.list(this.pageIndex, this.pageSize, this.status.value, this.rejectReason.value).subscribe({
      next: (page) => {
        this.rows.set(page.content);
        this.totalElements.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
