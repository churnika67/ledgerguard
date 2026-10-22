import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ChaosComponent } from './chaos.component';
import { ChaosRunResponse } from '../../core/models';
import { environment } from '../../../environments/environment';

function run(overrides: Partial<ChaosRunResponse>): ChaosRunResponse {
  return {
    id: 'r1',
    status: 'COMPLETED',
    strategy: 'OPTIMISTIC',
    requestedTransferCount: 100,
    accountCount: 10,
    concurrency: 20,
    startedAt: '2026-01-01T00:00:00Z',
    finishedAt: '2026-01-01T00:00:01Z',
    transfersPerSecond: 50,
    p95LatencyMillis: 120,
    retries: 3,
    conflicts: 2,
    failures: 0,
    invariantsHealthy: true,
    totalBalance: 1000,
    totalOpeningBalance: 1000,
    accountMismatchCount: 0,
    transferMismatchCount: 0,
    errorMessage: null,
    ...overrides,
  };
}

describe('ChaosComponent', () => {
  let fixture: ComponentFixture<ChaosComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChaosComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(ChaosComponent);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/admin/chaos-runs`).flush([]);
  });

  afterEach(() => httpMock.verify());

  it('builds retries/conflicts/failures bars from a completed run', () => {
    const data = fixture.componentInstance.outcomeChartData(run({ retries: 7, conflicts: 4, failures: 1 }));

    expect(data).toEqual([
      { name: 'Retries', value: 7 },
      { name: 'Conflicts', value: 4 },
      { name: 'Failures', value: 1 },
    ]);
  });

  it('rejects a transferCount over the 5000 cap at the form level', () => {
    fixture.componentInstance.form.patchValue({ transferCount: 5001 });

    expect(fixture.componentInstance.form.invalid).toBeTrue();
  });

  it('builds the recent-runs history chart oldest-first, capped at 8', () => {
    const runs = Array.from({ length: 10 }, (_, i) =>
      run({ id: `r${i}`, transfersPerSecond: i, status: 'COMPLETED' }),
    );
    fixture.componentInstance.recentRuns.set(runs);

    const data = fixture.componentInstance.historyChartData();

    // recentRuns() is assumed newest-first (as the API returns it); the chart takes the 8
    // most recent and reverses them so the x-axis reads oldest -> newest, left to right.
    expect(data.length).toBe(8);
    expect(data[0].value).toBe(7);
    expect(data[7].value).toBe(0);
  });
});
