// permission-granularity-model: human-readable labels for the raw
// `GlobalPermission`/`Permission` enum values the backend returns. Looks up
// `permissions.<value>` in the shared Transloco namespace and falls back to
// the raw value when the key is missing (REQ-14), relying on Transloco's own
// "key not found returns the key itself" default behavior instead of a
// custom try/catch. See PLAN.md's "Human-readable permission names" section.
import { TranslocoService } from '@jsverse/transloco';

export function translatePermissionLabel(value: string, transloco: TranslocoService): string {
  const key = `permissions.${value}`;
  const translated = transloco.translate(key);

  return translated === key ? value : translated;
}
