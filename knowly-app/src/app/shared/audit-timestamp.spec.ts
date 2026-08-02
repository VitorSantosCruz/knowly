import { formatAuditTimestamp } from './audit-timestamp';

describe('formatAuditTimestamp', () => {
  it('formats an ISO timestamp as dd/MM/yyyy HH:mm:ss in the local timezone', () => {
    const iso = '2026-08-02T14:05:09Z';
    const expected = new Intl.DateTimeFormat('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    })
      .format(new Date(iso))
      .replace(',', '');

    expect(formatAuditTimestamp(iso)).toBe(expected);
  });
});
