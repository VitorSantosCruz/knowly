// staff-members-management-redesign REQ-25: `dd/MM/yyyy HH:mm:ss`-shaped,
// browser-local-timezone rendering of an audit event's ISO timestamp. Uses
// `Intl.DateTimeFormat` with no explicit `timeZone` option (implicit local
// timezone) — no new date-formatting dependency, this codebase has none to
// reuse instead.
const FORMATTER = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
});

export function formatAuditTimestamp(iso: string): string {
  return FORMATTER.format(new Date(iso)).replace(',', '');
}
