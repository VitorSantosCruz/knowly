/**
 * Shared case-insensitive "contains" filter over a row's `displayName` — used by both
 * `ChatDirectoryComponent` (column 1's unified search) and `ChatFullDirectoryComponent`
 * (column 3's independent search), per Amendment (3)'s task 143 (extracted out of
 * `chat-directory.component.ts`, which used to define this as a private file-scope function,
 * so both components import one implementation instead of two copies).
 */
export function filterByQuery<T extends { displayName: string }>(rows: T[], query: string): T[] {
  const q = query.trim().toLowerCase();
  if (q === '') {
    return rows;
  }
  return rows.filter((row) => row.displayName.toLowerCase().includes(q));
}
