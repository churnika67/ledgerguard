import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSort, MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { AccountsService } from '../../core/accounts.service';
import { AccountResponse } from '../../core/models';

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  template: `
    <h1>Accounts</h1>
    <p class="subtitle">Every account in the ledger -- sortable, paginated, and searchable by owner name.</p>

    <mat-form-field appearance="outline" class="search">
      <mat-label>Search by owner name</mat-label>
      <input matInput [formControl]="search" placeholder="e.g. alice" />
      <mat-icon matSuffix>search</mat-icon>
    </mat-form-field>

    @if (loading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    <table mat-table [dataSource]="rows()" matSort (matSortChange)="onSort($event)" class="mat-elevation-z1 table">
      <ng-container matColumnDef="ownerName">
        <th mat-header-cell *matHeaderCellDef mat-sort-header>Owner</th>
        <td mat-cell *matCellDef="let a">{{ a.ownerName }}</td>
      </ng-container>
      <ng-container matColumnDef="currency">
        <th mat-header-cell *matHeaderCellDef>Currency</th>
        <td mat-cell *matCellDef="let a">{{ a.currency }}</td>
      </ng-container>
      <ng-container matColumnDef="balance">
        <th mat-header-cell *matHeaderCellDef mat-sort-header>Balance</th>
        <td mat-cell *matCellDef="let a">{{ a.balance | number: '1.2-2' }}</td>
      </ng-container>
      <ng-container matColumnDef="createdAt">
        <th mat-header-cell *matHeaderCellDef mat-sort-header>Opened</th>
        <td mat-cell *matCellDef="let a">{{ a.createdAt | date: 'short' }}</td>
      </ng-container>
      <ng-container matColumnDef="id">
        <th mat-header-cell *matHeaderCellDef>Account id</th>
        <td mat-cell *matCellDef="let a"><code>{{ a.id.slice(0, 8) }}</code></td>
      </ng-container>

      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns"></tr>
    </table>

    @if (!loading() && rows().length === 0) {
      <p class="empty">No accounts match.</p>
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
      .search {
        width: 320px;
      }
      .table {
        width: 100%;
      }
      .empty {
        padding: 24px;
        text-align: center;
        color: rgba(0, 0, 0, 0.5);
      }
    `,
  ],
})
export class AccountsComponent implements OnInit {
  readonly columns = ['ownerName', 'currency', 'balance', 'createdAt', 'id'];
  readonly rows = signal<AccountResponse[]>([]);
  readonly totalElements = signal(0);
  readonly loading = signal(false);
  readonly search = new FormControl('');

  pageSize = 10;
  private pageIndex = 0;
  private sortField = 'createdAt';
  private sortDirection: 'asc' | 'desc' = 'desc';
  private query = '';

  constructor(private accountsService: AccountsService) {}

  ngOnInit(): void {
    this.fetch();
    this.search.valueChanges.pipe(debounceTime(300), distinctUntilChanged()).subscribe((value) => {
      this.query = value ?? '';
      this.pageIndex = 0;
      this.fetch();
    });
  }

  onSort(sort: Sort): void {
    this.sortField = sort.direction ? sort.active : 'createdAt';
    this.sortDirection = sort.direction === 'asc' ? 'asc' : 'desc';
    this.fetch();
  }

  onPage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.fetch();
  }

  private fetch(): void {
    this.loading.set(true);
    const sort = `${this.sortField},${this.sortDirection}`;
    this.accountsService.list(this.pageIndex, this.pageSize, sort, this.query || null).subscribe({
      next: (page) => {
        this.rows.set(page.content);
        this.totalElements.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
