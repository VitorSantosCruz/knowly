import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { GroupVisibilityBadgeComponent } from './group-visibility-badge.component';
import { ChatGroupVisibility } from '../../core/chat.model';

describe('GroupVisibilityBadgeComponent', () => {
  let fixture: ComponentFixture<GroupVisibilityBadgeComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [GroupVisibilityBadgeComponent],
      providers: [
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(GroupVisibilityBadgeComponent);
  });

  it.each<ChatGroupVisibility>(['PRIVATE', 'REQUEST_TO_JOIN', 'PUBLIC'])(
    'renders a distinct label/style for %s',
    (visibility) => {
      fixture.componentRef.setInput('visibility', visibility);
      fixture.detectChanges();
      const badge = fixture.nativeElement.querySelector('[data-testid="group-visibility-badge"]');
      expect(badge.getAttribute('data-visibility')).toBe(visibility);
      expect(badge.textContent.trim().length).toBeGreaterThan(0);
    },
  );

  it('renders different styling classes across the three states', () => {
    const classesFor = (visibility: ChatGroupVisibility) => {
      fixture.componentRef.setInput('visibility', visibility);
      fixture.detectChanges();
      return fixture.nativeElement.querySelector('[data-testid="group-visibility-badge"]')
        .className;
    };
    const classes = new Set([
      classesFor('PRIVATE'),
      classesFor('REQUEST_TO_JOIN'),
      classesFor('PUBLIC'),
    ]);
    expect(classes.size).toBe(3);
  });
});
