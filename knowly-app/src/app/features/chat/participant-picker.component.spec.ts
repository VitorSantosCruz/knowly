import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { CandidateUser } from '../../core/chat.model';
import { ParticipantPickerComponent } from './participant-picker.component';

describe('ParticipantPickerComponent', () => {
  let fixture: ComponentFixture<ParticipantPickerComponent>;

  // Intentionally mixes a staff user and a plain member — the component must render both
  // as-is with no local staff/member/tenant filtering (REQ-2/REQ-3).
  const candidates: CandidateUser[] = [
    { userId: 1, nickname: 'staffer@knowly.com' },
    { userId: 2, nickname: 'member@tenant.com' },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ParticipantPickerComponent],
      providers: [
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(ParticipantPickerComponent);
    fixture.componentRef.setInput('candidates', candidates);
    fixture.detectChanges();
  });

  it('renders every candidate passed in, unfiltered', () => {
    const items = fixture.nativeElement.querySelectorAll(
      '[data-testid="participant-picker-candidate"]',
    );
    expect(items.length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('staffer@knowly.com');
    expect(fixture.nativeElement.textContent).toContain('member@tenant.com');
  });

  it('emits selectionChange with the toggled candidate id', () => {
    let emitted: number[] = [];
    fixture.componentInstance.selectionChange.subscribe((ids: number[]) => (emitted = ids));

    const checkbox: HTMLInputElement = fixture.nativeElement.querySelectorAll(
      '[data-testid="participant-picker-candidate"]',
    )[1];
    checkbox.dispatchEvent(new Event('change'));

    expect(emitted).toEqual([2]);
  });
});
