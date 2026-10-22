import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import LoginPage from "@/app/login/page";

const push = jest.fn();
jest.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

describe("LoginPage", () => {
  beforeEach(() => {
    push.mockClear();
    global.fetch = jest.fn();
  });

  it("logs in and redirects to /accounts on success", async () => {
    (global.fetch as jest.Mock).mockResolvedValue({ ok: true, json: async () => ({ ok: true }) });

    render(<LoginPage />);
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/accounts"));
    expect(global.fetch).toHaveBeenCalledWith(
      "/api/auth",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("shows the server's error message and does not redirect on failure", async () => {
    (global.fetch as jest.Mock).mockResolvedValue({
      ok: false,
      json: async () => ({ detail: "Bad credentials" }),
    });

    render(<LoginPage />);
    await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByText("Bad credentials")).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
  });
});
