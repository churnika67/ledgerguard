import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AccountsClient } from "@/components/AccountsClient";
import type { AccountResponse } from "@/lib/client-api";

jest.mock("@/lib/client-api", () => ({
  fetchAccounts: jest.fn().mockResolvedValue([]),
  openAccount: jest.fn(),
}));

function renderWithQueryClient(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe("AccountsClient", () => {
  const accounts: AccountResponse[] = [
    { id: "acc-1", ownerName: "alice", currency: "USD", balance: 100, createdAt: "2026-01-01T00:00:00Z" },
  ];

  it("renders the initial accounts passed from the server", () => {
    renderWithQueryClient(<AccountsClient initialAccounts={accounts} />);

    expect(screen.getByText("alice")).toBeInTheDocument();
    expect(screen.getByText("100.00 USD")).toBeInTheDocument();
  });

  it("shows an empty state when there are no accounts", () => {
    renderWithQueryClient(<AccountsClient initialAccounts={[]} />);

    expect(screen.getByText(/no accounts yet/i)).toBeInTheDocument();
  });

  it("renders the open-account form", () => {
    renderWithQueryClient(<AccountsClient initialAccounts={[]} />);

    expect(screen.getByRole("button", { name: /open account/i })).toBeInTheDocument();
  });
});
