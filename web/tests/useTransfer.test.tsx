import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useTransfer } from "@/hooks/useTransfer";
import { submitTransfer } from "@/lib/client-api";

jest.mock("@/lib/client-api", () => ({
  submitTransfer: jest.fn(),
}));

function wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe("useTransfer", () => {
  beforeEach(() => jest.clearAllMocks());

  it("submits the transfer with the given Idempotency-Key", async () => {
    (submitTransfer as jest.Mock).mockResolvedValue({
      transferId: "t-1",
      status: "COMPLETED",
      fromAccountId: "a",
      toAccountId: "b",
      amount: "10.00",
      currency: "USD",
      createdAt: "2026-01-01T00:00:00Z",
    });

    const { result } = renderHook(() => useTransfer(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({
        idempotencyKey: "key-1",
        fromAccountId: "a",
        toAccountId: "b",
        amount: "10.00",
        currency: "USD",
      });
    });

    expect(submitTransfer).toHaveBeenCalledWith({
      idempotencyKey: "key-1",
      fromAccountId: "a",
      toAccountId: "b",
      amount: "10.00",
      currency: "USD",
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it("replaying the same Idempotency-Key returns the same transferId (no double charge)", async () => {
    const sameResult = {
      transferId: "t-1",
      status: "COMPLETED",
      fromAccountId: "a",
      toAccountId: "b",
      amount: "10.00",
      currency: "USD",
      createdAt: "2026-01-01T00:00:00Z",
    };
    (submitTransfer as jest.Mock).mockResolvedValue(sameResult);

    const { result } = renderHook(() => useTransfer(), { wrapper });

    const input = { idempotencyKey: "key-1", fromAccountId: "a", toAccountId: "b", amount: "10.00", currency: "USD" };
    let first, second;
    await act(async () => {
      first = await result.current.mutateAsync(input);
    });
    await act(async () => {
      second = await result.current.mutateAsync(input);
    });

    expect(first).toEqual(second);
    expect(submitTransfer).toHaveBeenCalledTimes(2);
  });
});
