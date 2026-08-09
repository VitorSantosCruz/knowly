import { Type } from '@angular/core';
import {
  LucideArchive,
  LucideBookOpen,
  LucideBookmark,
  LucideBot,
  LucideBriefcase,
  LucideCode,
  LucideCompass,
  LucideFlag,
  LucideFolder,
  LucideGlobe,
  LucideGraduationCap,
  LucideHash,
  LucideHeart,
  LucideLayers,
  LucideLightbulb,
  LucideMessageCircle,
  LucideMessagesSquare,
  LucideNotebook,
  LucideRocket,
  LucideSparkles,
  LucideStar,
  LucideTag,
  LucideTarget,
  LucideUsers,
} from '@lucide/angular';
import { IconKey } from './chat.model';

/**
 * Amendment (4), REQ-38/REQ-39/REQ-40/REQ-13 (final round): single frontend source of truth
 * mapping each `IconKey` to its real `@lucide/angular` component + attribute selector + a
 * human-readable i18n key for its `aria-label` (never the raw enum key, per PLAN.md's a11y
 * convention) — reused by `icon-picker.component.ts`, `avatar.component.ts`'s icon rendering,
 * and the RAG/group conversation headers, per PLAN.md's "one lookup table, not three" decision.
 */
export interface IconRegistryEntry {
  component: Type<unknown>;
  /** Attribute selector (e.g. `lucideMessageCircle`) — `svg[lucideXxx]`, per this codebase's
   * established `@lucide/angular` convention (`shared/lucide-icons.spec.ts`). */
  selector: string;
  labelKey: string;
}

export const ICON_REGISTRY: Record<IconKey, IconRegistryEntry> = {
  MESSAGE_CIRCLE: {
    component: LucideMessageCircle,
    selector: 'lucideMessageCircle',
    labelKey: 'chat.iconPicker.icons.MESSAGE_CIRCLE',
  },
  MESSAGES_SQUARE: {
    component: LucideMessagesSquare,
    selector: 'lucideMessagesSquare',
    labelKey: 'chat.iconPicker.icons.MESSAGES_SQUARE',
  },
  BOOK_OPEN: {
    component: LucideBookOpen,
    selector: 'lucideBookOpen',
    labelKey: 'chat.iconPicker.icons.BOOK_OPEN',
  },
  NOTEBOOK: {
    component: LucideNotebook,
    selector: 'lucideNotebook',
    labelKey: 'chat.iconPicker.icons.NOTEBOOK',
  },
  SPARKLES: {
    component: LucideSparkles,
    selector: 'lucideSparkles',
    labelKey: 'chat.iconPicker.icons.SPARKLES',
  },
  BOT: { component: LucideBot, selector: 'lucideBot', labelKey: 'chat.iconPicker.icons.BOT' },
  USERS: {
    component: LucideUsers,
    selector: 'lucideUsers',
    labelKey: 'chat.iconPicker.icons.USERS',
  },
  HASH: { component: LucideHash, selector: 'lucideHash', labelKey: 'chat.iconPicker.icons.HASH' },
  FOLDER: {
    component: LucideFolder,
    selector: 'lucideFolder',
    labelKey: 'chat.iconPicker.icons.FOLDER',
  },
  STAR: { component: LucideStar, selector: 'lucideStar', labelKey: 'chat.iconPicker.icons.STAR' },
  HEART: {
    component: LucideHeart,
    selector: 'lucideHeart',
    labelKey: 'chat.iconPicker.icons.HEART',
  },
  FLAG: { component: LucideFlag, selector: 'lucideFlag', labelKey: 'chat.iconPicker.icons.FLAG' },
  TARGET: {
    component: LucideTarget,
    selector: 'lucideTarget',
    labelKey: 'chat.iconPicker.icons.TARGET',
  },
  ROCKET: {
    component: LucideRocket,
    selector: 'lucideRocket',
    labelKey: 'chat.iconPicker.icons.ROCKET',
  },
  LIGHTBULB: {
    component: LucideLightbulb,
    selector: 'lucideLightbulb',
    labelKey: 'chat.iconPicker.icons.LIGHTBULB',
  },
  GLOBE: {
    component: LucideGlobe,
    selector: 'lucideGlobe',
    labelKey: 'chat.iconPicker.icons.GLOBE',
  },
  COMPASS: {
    component: LucideCompass,
    selector: 'lucideCompass',
    labelKey: 'chat.iconPicker.icons.COMPASS',
  },
  GRADUATION_CAP: {
    component: LucideGraduationCap,
    selector: 'lucideGraduationCap',
    labelKey: 'chat.iconPicker.icons.GRADUATION_CAP',
  },
  BRIEFCASE: {
    component: LucideBriefcase,
    selector: 'lucideBriefcase',
    labelKey: 'chat.iconPicker.icons.BRIEFCASE',
  },
  ARCHIVE: {
    component: LucideArchive,
    selector: 'lucideArchive',
    labelKey: 'chat.iconPicker.icons.ARCHIVE',
  },
  TAG: { component: LucideTag, selector: 'lucideTag', labelKey: 'chat.iconPicker.icons.TAG' },
  BOOKMARK: {
    component: LucideBookmark,
    selector: 'lucideBookmark',
    labelKey: 'chat.iconPicker.icons.BOOKMARK',
  },
  LAYERS: {
    component: LucideLayers,
    selector: 'lucideLayers',
    labelKey: 'chat.iconPicker.icons.LAYERS',
  },
  CODE: { component: LucideCode, selector: 'lucideCode', labelKey: 'chat.iconPicker.icons.CODE' },
};
