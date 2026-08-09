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
  /** Amendment (4), REQ-40/REQ-13 (final round): additive, nullable — carried on the summary DTO
   * itself (`ChatConversationSummaryDto`, per `chat-group-naming-and-icon`'s backend PLAN), so
   * column 1's group rows can render it without a per-row detail fetch. `undefined` when a fixture
   * predates this amendment (defensive — the real backend always includes it). */
  icon?: IconKey | null;
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

/** REQ-13/18/26: a group conversation's visibility type, chosen at creation. */
export type ChatGroupVisibility = 'PRIVATE' | 'REQUEST_TO_JOIN' | 'PUBLIC';

/**
 * Amendment (4), REQ-38–REQ-41/REQ-13 (final round): the shared 24-value icon catalog, matching
 * `br.com.conectabyte.knowly.icon.IconKey` (backend) verbatim — one frontend source of truth
 * (`ICON_KEYS` below) reused by RAG-conversation and group creation/rename, rather than two
 * independent frontend enums drifting out of sync (see PLAN.md's "Amendment (4) reconciliation").
 */
export type IconKey =
  | 'MESSAGE_CIRCLE'
  | 'MESSAGES_SQUARE'
  | 'BOOK_OPEN'
  | 'NOTEBOOK'
  | 'SPARKLES'
  | 'BOT'
  | 'USERS'
  | 'HASH'
  | 'FOLDER'
  | 'STAR'
  | 'HEART'
  | 'FLAG'
  | 'TARGET'
  | 'ROCKET'
  | 'LIGHTBULB'
  | 'GLOBE'
  | 'COMPASS'
  | 'GRADUATION_CAP'
  | 'BRIEFCASE'
  | 'ARCHIVE'
  | 'TAG'
  | 'BOOKMARK'
  | 'LAYERS'
  | 'CODE';

export const ICON_KEYS: IconKey[] = [
  'MESSAGE_CIRCLE',
  'MESSAGES_SQUARE',
  'BOOK_OPEN',
  'NOTEBOOK',
  'SPARKLES',
  'BOT',
  'USERS',
  'HASH',
  'FOLDER',
  'STAR',
  'HEART',
  'FLAG',
  'TARGET',
  'ROCKET',
  'LIGHTBULB',
  'GLOBE',
  'COMPASS',
  'GRADUATION_CAP',
  'BRIEFCASE',
  'ARCHIVE',
  'TAG',
  'BOOKMARK',
  'LAYERS',
  'CODE',
];

export interface ConversationDetail {
  id: number;
  kind: ChatConversationKind;
  tenantId: number | null;
  title: string | null;
  participantUserIds: number[];
  participantNicknames: Record<number, string>;
  /** Additive fields matching the backend's extended `ChatConversationDetailDto`
   * (chat-unified-ui PLAN.md, "Consumed API contracts"). */
  visibility: ChatGroupVisibility;
  archivedAt: string | null;
  adminUserIds: number[];
  /** Amendment (4): additive, nullable — `null` for every pre-Amendment-(4) group (V32
   * backfill leaves `icon` untouched for existing rows). */
  icon: IconKey | null;
}

/** REQ-8's Groups candidate set — `GET /api/chat/discoverable-groups`. */
export interface ChatDiscoverableGroupDto {
  id: number;
  title: string;
  tenantId: number | null;
  visibility: ChatGroupVisibility;
  participantCount: number;
}

/** `GET/POST .../join-requests` — the `requestedAt` field this PLAN originally guessed
 * does not exist on the wire; only `status`/`decidedAt` do. */
export interface ChatJoinRequestDto {
  id: number;
  conversationId: number;
  requesterUserId: number;
  requesterNickname: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  decidedAt: string | null;
}

/** `POST .../participants` — a batch add can partially succeed (200), never all-or-nothing. */
export interface ChatAddParticipantsResultDto {
  conversation: ConversationDetail;
  rejected: { userId: number; reason: 'ALREADY_PARTICIPANT' | 'INELIGIBLE' }[];
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

/** Client-only per-message send state (REQ-5/REQ-6) — 'streaming' additionally covers the
 * knowledge-base assistant's in-progress reply (empty/growing content, no id round-trip yet). */
export type MessageSendState = 'pending' | 'failed' | 'streaming' | undefined;

export interface DisplayMessage extends Message {
  sendState?: MessageSendState;
  /** Correlates an optimistic message with its eventual server-confirmed replacement. */
  localId?: string;
  /** True when the viewer themself is the sender — drives the self-end/self-start bubble
   * alignment shared by peer/group chats and knowledge-base conversations alike. */
  fromViewer?: boolean;
}

export interface CreateConversationRequest {
  kind: CreateChatConversationKind;
  tenantId?: number | null;
  title?: string;
  /** Required when `kind === 'GROUP'` (REQ-13/18); absent/ignored for `'DIRECT'`. */
  visibility?: ChatGroupVisibility;
  participantUserIds: number[];
  /** Amendment (4), REQ-13 (final round): optional icon, forwarded verbatim to
   * `POST /api/chat/conversations` when chosen; omitted when not (group keeps its existing
   * default/fallback presentation). */
  icon?: IconKey;
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
