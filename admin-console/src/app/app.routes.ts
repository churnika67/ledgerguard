import { Routes } from '@angular/router';
import { adminGuard } from './core/admin.guard';
import { ShellComponent } from './layout/shell.component';
import { LoginComponent } from './features/login/login.component';
import { ReconciliationComponent } from './features/reconciliation/reconciliation.component';
import { AccountsComponent } from './features/accounts/accounts.component';
import { TransfersComponent } from './features/transfers/transfers.component';
import { ChaosComponent } from './features/chaos/chaos.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: ShellComponent,
    canActivate: [adminGuard],
    children: [
      { path: '', redirectTo: 'reconciliation', pathMatch: 'full' },
      { path: 'reconciliation', component: ReconciliationComponent },
      { path: 'accounts', component: AccountsComponent },
      { path: 'transfers', component: TransfersComponent },
      { path: 'chaos', component: ChaosComponent },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
