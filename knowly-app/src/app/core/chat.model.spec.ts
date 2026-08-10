import { describe, expect, it } from 'vitest';
import { splitOnMatch } from './chat.model';

/**
 * `chat-message-search` PLAN.md, Amended (2026-08-10) — REQ-32's literal, case-insensitive,
 * first-occurrence-only substring match used both by `chat-search-result-row.component.ts`
 * (result list) and `message-thread.component.ts` (persistent in-bubble highlight, REQ-36).
 */
describe('splitOnMatch', () => {
  it('splits the text into before/match/after around the first case-insensitive match', () => {
    expect(splitOnMatch('andamento do Relatório mensal', 'relatório')).toEqual({
      before: 'andamento do ',
      match: 'Relatório',
      after: ' mensal',
    });
  });

  it('only splits the first occurrence when the query appears more than once', () => {
    expect(splitOnMatch('teste teste teste', 'teste')).toEqual({
      before: '',
      match: 'teste',
      after: ' teste teste',
    });
  });

  it('returns null when the query does not literally substring-match the text', () => {
    expect(splitOnMatch('andamento do relatório', 'orçamento')).toBeNull();
  });

  it('returns null for an empty/blank query instead of matching everything', () => {
    expect(splitOnMatch('andamento do relatório', '')).toBeNull();
    expect(splitOnMatch('andamento do relatório', '   ')).toBeNull();
  });

  it('preserves the original casing of the matched substring in the `match` segment', () => {
    const result = splitOnMatch('RELATÓRIO mensal', 'relatório');
    expect(result?.match).toBe('RELATÓRIO');
  });
});
