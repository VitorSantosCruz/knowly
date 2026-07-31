// DTO shapes matching knowly-api's real internal-team-chat implementation
// (br.com.conectabyte.knowly.chat.dto), read directly from the backend source rather than
// PLAN.md's earlier provisional shapes, which drifted during backend implementation. See
// specify/features/internal-team-chat/TASKS.md task 2 and PLAN.md's "Emergent decisions"
// section for the differences called out below.

export type ChatConversationKind = 'PEER_DIRECT' | 'PEER_GROUP' | 'SUPPORT';
export type CreateChatConversationKind = 'DIRECT' | 'GROUP';

/**
 * Deviation from PLAN.md: the backend's `ChatConversationSummaryDto` has no
 * `participantNicknames`, `viewerRelation`, `lastMessagePreview`, or `lastMessageAt` fields —
 * it only carries `participantUserIds`. Nicknames are only available via the detail endpoint
 * (`ChatConversationDetailDto.participantNicknames`). The list screen therefore renders the
 * conversation's `title` (when set) or a generic "N participants" fallback, not nicknames —
 * see `conversation-list-item.component.ts`.
 */
export interface ConversationSummary {
  id: number;
  kind: ChatConversationKind;
  tenantId: number | null;
  title: string | null;
  participantUserIds: number[];
}

/**
 * Deviation from PLAN.md: no `viewerRelation` field exists on the wire at all — the backend
 * never marks a look-in view as such. It's derived client-side instead: the backend's
 * `requireReadableConversation` guarantees a viewer who can read a PEER_GROUP/SUPPORT
 * conversation without a genuine `chat_participants` row is, by construction, either a
 * `STAFF_ADMIN`/`MEMBER_ADMIN` look-in or a support-eligible viewer — so
 * "current user id absent from `participantUserIds`" is a safe, sufficient signal. See
 * `deriveViewerRelation` below.
 */
export type ViewerRelation = 'PARTICIPANT' | 'LOOKING_IN';

export function deriveViewerRelation(
  participantUserIds: number[],
  currentUserId: number | null,
): ViewerRelation {
  if (currentUserId !== null && participantUserIds.includes(currentUserId)) {
    return 'PARTICIPANT';
  }
  return 'LOOKING_IN';
}

export interface ConversationDetail {
  id: number;
  kind: ChatConversationKind;
  tenantId: number | null;
  title: string | null;
  participantUserIds: number[];
  participantNicknames: Record<number, string>;
}

export interface CandidateUser {
  userId: number;
  nickname: string;
}

export interface Message {
  id: number;
  senderUserId: number;
  senderNickname: string;
  content: string;
  createdAt: string;
}

export interface MessagePage {
  messages: Message[];
  nextCursor: string | null;
}

/** Client-only per-message send state (REQ-5/REQ-6) — never part of the API response. */
export type MessageSendState = 'pending' | 'failed' | undefined;

export interface DisplayMessage extends Message {
  sendState?: MessageSendState;
  /** Correlates an optimistic message with its eventual server-confirmed replacement. */
  localId?: string;
}

export interface CreateConversationRequest {
  kind: CreateChatConversationKind;
  tenantId?: number | null;
  title?: string;
  participantUserIds: number[];
}

export type EligibilityScope = 'direct' | 'group' | 'group-staff-only';

/** Matches the backend's `SupportTicketStatus` enum exactly (three states, not two). */
export type SupportTicketStatus = 'OPEN' | 'ASSIGNED' | 'CLOSED';

/**
 * Deviation from PLAN.md: the backend's `SupportTicketDto` has no `assignedStaffNickname`
 * and uses `openedAt`/`supportChannelId`, not `createdAt`/a `SupportChannelSummary` envelope.
 */
export interface TicketSummary {
  id: number;
  supportChannelId: number;
  status: SupportTicketStatus;
  assignedStaffUserId: number | null;
  openedAt: string;
  closedAt: string | null;
}

export interface MessageCacheEntry {
  messages: DisplayMessage[];
  hasMore: boolean;
  oldestCursor: string | null;
  newestCursor: string | null;
  loadError: boolean;
  loading: boolean;
}

export function emptyMessageCacheEntry(): MessageCacheEntry {
  return {
    messages: [],
    hasMore: false,
    oldestCursor: null,
    newestCursor: null,
    loadError: false,
    loading: false,
  };
}
