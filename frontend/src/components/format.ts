const dateTime = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false });
const date = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 });
const qty = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 3 });
const oneDecimal = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

export const fmtDateTime = (iso: string | null | undefined) => (iso ? dateTime.format(new Date(iso)) : '—');
export const fmtDate = (iso: string | null | undefined) => (iso ? date.format(new Date(iso)) : '—');
export const fmtMoney = (n: number) => inr.format(n);
export const fmtQty = (n: number) => qty.format(n);
export const fmtHours = (n: number | null | undefined) => (n == null ? '—' : `${oneDecimal.format(n)} h`);

/** 95 → "1 h 35 min". Durations are integer minutes end to end. */
export function fmtMinutes(m: number | null | undefined): string {
  if (m == null) return '—';
  const h = Math.floor(m / 60);
  const min = m % 60;
  if (h === 0) return `${min} min`;
  return min === 0 ? `${h} h` : `${h} h ${min} min`;
}
