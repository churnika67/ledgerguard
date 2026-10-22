export interface AuthRequest {
  username: string;
  password: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export interface AccountResponse {
  id: string;
  ownerName: string;
  currency: string;
  balance: number;
  createdAt: string;
}

export type TransferStatus = 'COMPLETED' | 'REJECTED';
export type RejectReason = 'INSUFFICIENT_FUNDS' | 'SAME_ACCOUNT' | 'CURRENCY_MISMATCH';

export interface TransferResponse {
  transferId: string;
  status: TransferStatus;
  rejectReason: RejectReason | null;
  fromAccountId: string;
  toAccountId: string;
  amount: number;
  currency: string;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ReconciliationResponse {
  healthy: boolean;
  totalBalance: number;
  totalOpeningBalance: number;
  accountsWithMismatchedBalance: string[];
  transfersNotNettingToZero: string[];
}

export type ChaosRunStatus = 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface ChaosRunRequest {
  transferCount: number;
  accountCount: number;
  concurrency: number;
}

export interface ChaosRunResponse {
  id: string;
  status: ChaosRunStatus;
  strategy: string;
  requestedTransferCount: number;
  accountCount: number;
  concurrency: number;
  startedAt: string;
  finishedAt: string | null;
  transfersPerSecond: number | null;
  p95LatencyMillis: number | null;
  retries: number | null;
  conflicts: number | null;
  failures: number | null;
  invariantsHealthy: boolean | null;
  totalBalance: number | null;
  totalOpeningBalance: number | null;
  accountMismatchCount: number | null;
  transferMismatchCount: number | null;
  errorMessage: string | null;
}
