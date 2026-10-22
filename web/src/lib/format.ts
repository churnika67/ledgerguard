/** Formats a money amount as returned by the API (a JSON number) with exactly 2 decimal places. */
export function formatMoney(value: number | undefined): string {
  return (value ?? 0).toFixed(2);
}
