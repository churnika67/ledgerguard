import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Color, NgxChartsModule, ScaleType } from '@swimlane/ngx-charts';
import { ChaosService } from '../../core/chaos.service';
import { ChaosRunResponse } from '../../core/models';

@Component({
  selector: 'app-chaos',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    NgxChartsModule,
  ],
  template: `
    <h1>Chaos test</h1>
    <p class="subtitle">
      Fires a bounded burst of concurrent transfers (max 5,000) at fresh demo accounts, on whichever
      locking strategy is currently live, then checks the ledger's invariants still hold.
    </p>

    <mat-card class="form-card">
      <form [formGroup]="form" (ngSubmit)="run()" class="form">
        <mat-form-field appearance="outline">
          <mat-label>Transfer count (max 5000)</mat-label>
          <input matInput type="number" formControlName="transferCount" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Account count (2-100)</mat-label>
          <input matInput type="number" formControlName="accountCount" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Concurrency (1-200)</mat-label>
          <input matInput type="number" formControlName="concurrency" />
        </mat-form-field>
        <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || running()">
          <mat-icon>bolt</mat-icon>
          Run chaos test
        </button>
      </form>
    </mat-card>

    @if (running()) {
      <div class="running">
        <mat-spinner diameter="28" />
        <span>Running against {{ form.value.accountCount }} fresh accounts...</span>
      </div>
    }

    @if (current(); as run) {
      @if (run.status === 'COMPLETED') {
        <div class="result">
          <div class="status-card" [class.healthy]="run.invariantsHealthy" [class.unhealthy]="!run.invariantsHealthy">
            <mat-icon>{{ run.invariantsHealthy ? 'check_circle' : 'error' }}</mat-icon>
            <span>
              {{ run.invariantsHealthy ? 'Invariants held' : 'Invariant violation!' }} -- strategy:
              <strong>{{ run.strategy }}</strong>
            </span>
          </div>

          <div class="grid">
            <mat-card>
              <mat-card-header><mat-card-title>Transfers/sec</mat-card-title></mat-card-header>
              <mat-card-content>
                <div class="metric">{{ run.transfersPerSecond | number: '1.1-1' }}</div>
              </mat-card-content>
            </mat-card>
            <mat-card>
              <mat-card-header><mat-card-title>p95 latency</mat-card-title></mat-card-header>
              <mat-card-content><div class="metric">{{ run.p95LatencyMillis }} ms</div></mat-card-content>
            </mat-card>
            <mat-card>
              <mat-card-header><mat-card-title>Retries</mat-card-title></mat-card-header>
              <mat-card-content><div class="metric">{{ run.retries }}</div></mat-card-content>
            </mat-card>
            <mat-card>
              <mat-card-header><mat-card-title>Conflicts</mat-card-title></mat-card-header>
              <mat-card-content><div class="metric">{{ run.conflicts }}</div></mat-card-content>
            </mat-card>
            <mat-card>
              <mat-card-header><mat-card-title>Failures</mat-card-title></mat-card-header>
              <mat-card-content><div class="metric">{{ run.failures }}</div></mat-card-content>
            </mat-card>
          </div>

          <div class="chart-card">
            <h2>Retries vs conflicts vs failures</h2>
            <ngx-charts-bar-vertical
              [results]="outcomeChartData(run)"
              [xAxis]="true"
              [yAxis]="true"
              [scheme]="colorScheme"
              [view]="[600, 260]"
            />
          </div>
        </div>
      } @else if (run.status === 'FAILED') {
        <p class="error">Chaos run failed: {{ run.errorMessage }}</p>
      }
    }

    @if (recentRuns().length > 1) {
      <div class="chart-card">
        <h2>Recent runs -- transfers/sec</h2>
        <ngx-charts-bar-vertical
          [results]="historyChartData()"
          [xAxis]="true"
          [yAxis]="true"
          [scheme]="colorScheme"
          [view]="[700, 260]"
        />
      </div>
    }
  `,
  styles: [
    `
      .subtitle {
        color: rgba(0, 0, 0, 0.6);
        max-width: 680px;
      }
      .form-card {
        margin-bottom: 20px;
      }
      .form {
        display: flex;
        gap: 16px;
        flex-wrap: wrap;
        align-items: flex-start;
        padding-top: 8px;
      }
      .form button {
        height: 56px;
      }
      .running {
        display: flex;
        align-items: center;
        gap: 12px;
        margin: 16px 0;
        color: rgba(0, 0, 0, 0.7);
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
        grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
        gap: 16px;
        margin-bottom: 24px;
      }
      .metric {
        font-size: 1.6rem;
        font-weight: 600;
      }
      .chart-card {
        background: white;
        border-radius: 8px;
        padding: 16px;
        margin-top: 16px;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
      }
      .chart-card h2 {
        font-size: 1rem;
        margin: 0 0 8px;
      }
      .error {
        color: #b3261e;
      }
    `,
  ],
})
export class ChaosComponent implements OnInit, OnDestroy {
  readonly running = signal(false);
  readonly current = signal<ChaosRunResponse | null>(null);
  readonly recentRuns = signal<ChaosRunResponse[]>([]);

  readonly colorScheme: Color = {
    name: 'ledgerguard',
    selectable: true,
    group: ScaleType.Ordinal,
    domain: ['#3f51b5', '#5c6bc0', '#7986cb', '#9fa8da'],
  };

  form = this.fb.group({
    transferCount: [500, [Validators.required, Validators.min(1), Validators.max(5000)]],
    accountCount: [20, [Validators.required, Validators.min(2), Validators.max(100)]],
    concurrency: [50, [Validators.required, Validators.min(1), Validators.max(200)]],
  });

  private pollHandle: ReturnType<typeof setInterval> | null = null;

  constructor(
    private fb: FormBuilder,
    private chaosService: ChaosService,
  ) {}

  ngOnInit(): void {
    this.loadRecent();
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  run(): void {
    if (this.form.invalid) {
      return;
    }
    const { transferCount, accountCount, concurrency } = this.form.getRawValue();
    this.running.set(true);
    this.current.set(null);
    this.chaosService
      .start({ transferCount: transferCount!, accountCount: accountCount!, concurrency: concurrency! })
      .subscribe({
        next: (run) => {
          this.current.set(run);
          this.poll(run.id);
        },
        error: () => this.running.set(false),
      });
  }

  outcomeChartData(run: ChaosRunResponse) {
    return [
      { name: 'Retries', value: run.retries ?? 0 },
      { name: 'Conflicts', value: run.conflicts ?? 0 },
      { name: 'Failures', value: run.failures ?? 0 },
    ];
  }

  historyChartData() {
    return this.recentRuns()
      .filter((r) => r.status === 'COMPLETED')
      .slice(0, 8)
      .reverse()
      .map((r, i) => ({ name: `Run ${i + 1}`, value: r.transfersPerSecond ?? 0 }));
  }

  private poll(id: string): void {
    this.stopPolling();
    this.pollHandle = setInterval(() => {
      this.chaosService.get(id).subscribe((run) => {
        this.current.set(run);
        if (run.status !== 'RUNNING') {
          this.running.set(false);
          this.stopPolling();
          this.loadRecent();
        }
      });
    }, 500);
  }

  private stopPolling(): void {
    if (this.pollHandle) {
      clearInterval(this.pollHandle);
      this.pollHandle = null;
    }
  }

  private loadRecent(): void {
    this.chaosService.listRecent().subscribe((runs) => this.recentRuns.set(runs));
  }
}
