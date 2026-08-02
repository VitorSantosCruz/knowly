// staff-members-management-redesign REQ-27/REQ-27a: human-readable phrases
// for the raw `action` string literals written by every `@AuditLog`/
// `AuditEventWriter` call site in knowly-api (inventoried by grepping
// knowly-api/src/main/java for every `@AuditLog(action = "...")` and direct
// `AuditEventWriter` write). Looks up `auditActions.<action>` in the shared
// Transloco namespace and falls back to the raw action string when the key
// is missing, same mechanism as permission-labels.ts.
import { TranslocoService } from '@jsverse/transloco';

export function translateAuditAction(action: string, transloco: TranslocoService): string {
  const key = `auditActions.${action}`;
  const translated = transloco.translate(key);

  return translated === key ? action : translated;
}
