import { Injectable, inject, signal } from '@angular/core';
import { OnboardingService } from './onboarding.service';

export interface TourStep {
  id: string;
  targetId: string;
  titleKey: string;
  bodyKey: string;
}

const STEPS: TourStep[] = [
  {
    id: 'main-nav',
    targetId: 'main-nav',
    titleKey: 'tour.mainNav.title',
    bodyKey: 'tour.mainNav.body',
  },
  {
    id: 'articles',
    targetId: 'articles-nav-link',
    titleKey: 'tour.articles.title',
    bodyKey: 'tour.articles.body',
  },
  {
    id: 'user-management',
    targetId: 'user-management-nav-link',
    titleKey: 'tour.userManagement.title',
    bodyKey: 'tour.userManagement.body',
  },
  {
    id: 'bot-config',
    targetId: 'bot-config-nav-link',
    titleKey: 'tour.botConfig.title',
    bodyKey: 'tour.botConfig.body',
  },
  {
    id: 'help-menu',
    targetId: 'help-menu',
    titleKey: 'tour.helpMenu.title',
    bodyKey: 'tour.helpMenu.body',
  },
];

@Injectable({ providedIn: 'root' })
export class TourService {
  private readonly onboardingService = inject(OnboardingService);

  readonly steps: readonly TourStep[] = STEPS;

  private readonly _active = signal(false);
  readonly active = this._active.asReadonly();

  private readonly _stepIndex = signal(0);
  readonly stepIndex = this._stepIndex.asReadonly();

  start(): void {
    this._stepIndex.set(0);
    this._active.set(true);
  }

  next(): void {
    if (this._stepIndex() >= this.steps.length - 1) {
      this.finish();
      return;
    }

    this._stepIndex.update((index) => index + 1);
  }

  back(): void {
    this._stepIndex.update((index) => Math.max(0, index - 1));
  }

  skip(): void {
    this.finish();
  }

  private finish(): void {
    this._active.set(false);
    this.onboardingService.markComplete().subscribe();
  }
}
