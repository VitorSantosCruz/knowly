package br.com.conectabyte.knowly.icon;

/**
 * Fixed, server-validated catalog of icon keys a conversation (RAG {@code Conversation} or group
 * {@code ChatConversation}) may be tagged with. Shared cross-cutting package (2026-08-09, see
 * DECISIONS.md "shared IconKey enum in a new small cross-cutting package") rather than duplicating
 * the list per entity, so both icon pickers can never drift apart.
 *
 * <p>Each value's name matches (once converted to {@code @lucide/angular}'s {@code
 * Lucide<PascalCase>} component-export convention, e.g. {@code MESSAGE_CIRCLE} -> {@code
 * LucideMessageCircle}) a real icon already available in the frontend's {@code @lucide/angular}
 * dependency (confirmed against {@code knowly-app/node_modules/@lucide/angular}'s type declarations
 * at implementation time, 2026-08-09) -- not an independently-invented list, so the frontend can
 * wire directly to these string values without a translation layer. A curated starter set of common
 * conversation/group-appropriate icons, not an exhaustive mirror of every Lucide icon.
 */
public enum IconKey {
    MESSAGE_CIRCLE,
    MESSAGES_SQUARE,
    BOOK_OPEN,
    NOTEBOOK,
    SPARKLES,
    BOT,
    USERS,
    HASH,
    FOLDER,
    STAR,
    HEART,
    FLAG,
    TARGET,
    ROCKET,
    LIGHTBULB,
    GLOBE,
    COMPASS,
    GRADUATION_CAP,
    BRIEFCASE,
    ARCHIVE,
    TAG,
    BOOKMARK,
    LAYERS,
    CODE
}
