export interface NotificationResponse {
  id: string;
  accountId: string;
  transferId: string;
  type: "PAYMENT_SENT" | "PAYMENT_RECEIVED" | "PAYMENT_FAILED";
  message: string;
  createdAt: string;
}
