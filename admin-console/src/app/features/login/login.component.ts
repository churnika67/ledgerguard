import { Component, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="wrap">
      <mat-card class="card">
        <div class="brand-badge">
          <mat-icon>shield</mat-icon>
        </div>
        <mat-card-header>
          <mat-card-title>LedgerGuard Admin</mat-card-title>
          <mat-card-subtitle>Back-office console -- ADMIN access only</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="submit()" class="form">
            <mat-form-field appearance="outline">
              <mat-label>Username</mat-label>
              <input matInput formControlName="username" autocomplete="username" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Password</mat-label>
              <input matInput type="password" formControlName="password" autocomplete="current-password" />
            </mat-form-field>
            @if (error()) {
              <p class="error">{{ error() }}</p>
            }
            <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || loading()">
              @if (loading()) {
                <mat-spinner diameter="18" />
              } @else {
                Sign in
              }
            </button>
          </form>
          <p class="hint">Demo: <code>admin</code>/<code>admin123</code></p>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [
    `
      .wrap {
        display: flex;
        min-height: 100vh;
        align-items: center;
        justify-content: center;
        padding: 16px;
      }
      .card {
        width: 100%;
        max-width: 380px;
        text-align: center;
      }
      .brand-badge {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 56px;
        height: 56px;
        margin: 20px auto 0;
        border-radius: 16px;
        background: linear-gradient(135deg, #3f51b5, #5c6bc0);
        color: white;
        box-shadow: 0 4px 12px rgba(63, 81, 181, 0.35);
      }
      ::ng-deep mat-card-header .mdc-card__title {
        text-align: center;
      }
      ::ng-deep mat-card-header .mdc-card__subtitle {
        text-align: center;
      }
      .form {
        display: flex;
        flex-direction: column;
        gap: 4px;
        margin-top: 12px;
      }
      .error {
        color: #b3261e;
        font-size: 0.85rem;
        margin: 0 0 8px;
      }
      .hint {
        color: rgba(0, 0, 0, 0.6);
        font-size: 0.8rem;
        margin-top: 16px;
      }
    `,
  ],
})
export class LoginComponent {
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  form = this.fb.group({
    username: ['admin', Validators.required],
    password: ['admin123', Validators.required],
  });

  constructor(
    private fb: FormBuilder,
    private auth: AuthService,
    private router: Router,
  ) {}

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const { username, password } = this.form.getRawValue();
    this.auth.login({ username: username!, password: password! }).subscribe({
      next: () => {
        this.loading.set(false);
        if (!this.auth.isAdmin()) {
          this.error.set('This console requires an ADMIN account. Try admin/admin123.');
          this.auth.logout();
          return;
        }
        this.router.navigate(['/']);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Invalid username or password.');
      },
    });
  }
}
