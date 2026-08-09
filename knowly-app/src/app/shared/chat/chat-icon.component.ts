import { Component, input } from '@angular/core';
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
import { IconKey } from '../../core/chat.model';

/**
 * Amendment (4): renders a single `IconKey`'s Lucide icon (or nothing, when `icon()` is `null`,
 * letting the host fall back to its own default presentation — e.g. `avatar.component.ts`'s
 * existing generic fallback) — reused by `icon-picker.component.ts`'s 24-button grid, directory
 * row rendering (13g), and the RAG/group conversation headers (13d/13f). A `@switch` over the
 * fixed, imported icon set (not a `Type<unknown>` looked up and passed to `NgComponentOutlet`)
 * keeps every icon statically imported/tree-shaken, consistent with this codebase's established
 * `@lucide/angular` convention (attribute-selector components, no dynamic-component wiring).
 */
@Component({
  selector: 'app-chat-icon',
  imports: [
    LucideMessageCircle,
    LucideMessagesSquare,
    LucideBookOpen,
    LucideNotebook,
    LucideSparkles,
    LucideBot,
    LucideUsers,
    LucideHash,
    LucideFolder,
    LucideStar,
    LucideHeart,
    LucideFlag,
    LucideTarget,
    LucideRocket,
    LucideLightbulb,
    LucideGlobe,
    LucideCompass,
    LucideGraduationCap,
    LucideBriefcase,
    LucideArchive,
    LucideTag,
    LucideBookmark,
    LucideLayers,
    LucideCode,
  ],
  template: `
    @switch (icon()) {
      @case ('MESSAGE_CIRCLE') {
        <svg
          lucideMessageCircle
          [attr.data-testid]="'chat-icon-' + icon()"
          aria-hidden="true"
        ></svg>
      }
      @case ('MESSAGES_SQUARE') {
        <svg
          lucideMessagesSquare
          [attr.data-testid]="'chat-icon-' + icon()"
          aria-hidden="true"
        ></svg>
      }
      @case ('BOOK_OPEN') {
        <svg lucideBookOpen [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
      @case ('NOTEBOOK') {
        <svg lucideNotebook [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
      @case ('SPARKLES') {
        <svg lucideSparkles [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
      @case ('BOT') {
        <svg lucideBot [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
      @case ('USERS') {
        <svg lucideUsers [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
      @case ('HASH') {
        <svg lucideHash [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
      @case ('FOLDER') {
        <svg lucideFolder [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
      @case ('STAR') {
        <svg lucideStar [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
      @case ('HEART') {
        <svg lucideHeart [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
      @case ('FLAG') {
        <svg lucideFlag [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
      @case ('TARGET') {
        <svg lucideTarget [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
      @case ('ROCKET') {
        <svg lucideRocket [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
      @case ('LIGHTBULB') {
        <svg lucideLightbulb [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
      @case ('GLOBE') {
        <svg lucideGlobe [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
      @case ('COMPASS') {
        <svg lucideCompass [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
      @case ('GRADUATION_CAP') {
        <svg
          lucideGraduationCap
          [attr.data-testid]="'chat-icon-' + icon()"
          aria-hidden="true"
        ></svg>
      }
      @case ('BRIEFCASE') {
        <svg lucideBriefcase [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
      @case ('ARCHIVE') {
        <svg lucideArchive [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
      @case ('TAG') {
        <svg lucideTag [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
      @case ('BOOKMARK') {
        <svg lucideBookmark [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
      @case ('LAYERS') {
        <svg lucideLayers [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
      @case ('CODE') {
        <svg lucideCode [attr.data-testid]="'chat-icon-' + icon()" aria-hidden="true"></svg>
      }
    }
  `,
})
export class ChatIconComponent {
  readonly icon = input.required<IconKey | null>();
}
