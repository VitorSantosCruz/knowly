import { Component, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-avatar-upload',
  imports: [TranslocoPipe],
  template: `
    <div data-testid="avatar-upload" class="flex items-center gap-3">
      @if (avatarUrl()) {
        <img
          data-testid="avatar-upload-image"
          [src]="avatarUrl()"
          alt=""
          class="h-16 w-16 rounded-full object-cover"
        />
      } @else {
        <div
          data-testid="avatar-upload-placeholder"
          class="flex h-16 w-16 items-center justify-center rounded-full bg-ink-100 text-xs text-ink-500 dark:bg-ink-800 dark:text-ink-400"
        >
          {{ 'profile.avatar.title' | transloco }}
        </div>
      }

      <label
        class="cursor-pointer rounded-xl border border-ink-300/70 px-3 py-1.5 text-sm font-medium text-ink-700 transition-colors duration-fast ease-fluid hover:bg-ink-50 dark:border-ink-700 dark:text-ink-300 dark:hover:bg-ink-800/50"
      >
        {{ 'profile.avatar.change' | transloco }}
        <input
          data-testid="avatar-upload-input"
          type="file"
          accept="image/*"
          class="hidden"
          (change)="onFileSelected($event)"
        />
      </label>
    </div>
  `,
})
export class AvatarUploadComponent {
  readonly avatarUrl = input<string | null>(null);
  readonly fileSelected = output<File>();

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];

    if (file) {
      this.fileSelected.emit(file);
    }

    input.value = '';
  }
}
