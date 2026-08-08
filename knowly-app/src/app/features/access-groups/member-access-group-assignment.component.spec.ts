import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { MemberAccessGroupAssignmentComponent } from './member-access-group-assignment.component';
import { AccessGroup } from '../../core/member.service';

const GROUPS: AccessGroup[] = [
  { id: 1, name: 'Editors', permissions: [] },
  { id: 2, name: 'Reviewers', permissions: [] },
  { id: 3, name: 'Admins', permissions: [] },
];

describe('MemberAccessGroupAssignmentComponent', () => {
  let fixture: ComponentFixture<MemberAccessGroupAssignmentComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MemberAccessGroupAssignmentComponent],
      providers: [
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MemberAccessGroupAssignmentComponent);
    fixture.componentRef.setInput('allGroups', GROUPS);
    fixture.componentRef.setInput('assignedGroupIds', new Set([2]));
    fixture.detectChanges();
  });

  function checkbox(id: number): HTMLInputElement {
    return fixture.nativeElement.querySelector(`[data-testid="group-checkbox-${id}"]`);
  }

  it('renders one checkbox per allGroups entry, pre-checked for ids in assignedGroupIds', () => {
    expect(checkbox(1).checked).toBe(false);
    expect(checkbox(2).checked).toBe(true);
    expect(checkbox(3).checked).toBe(false);
  });

  it('emits submitted with exactly the checked id set, including a single-id selection', () => {
    let emitted: number[] | undefined;
    fixture.componentInstance.submitted.subscribe((ids: number[]) => (emitted = ids));

    checkbox(1).click();
    fixture.detectChanges();
    checkbox(2).click();
    fixture.detectChanges();

    const submitButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="group-assignment-submit"]',
    );
    submitButton.click();

    expect(emitted).toEqual([1]);
  });

  it('emits submitted with more than one id when several boxes are checked', () => {
    let emitted: number[] | undefined;
    fixture.componentInstance.submitted.subscribe((ids: number[]) => (emitted = ids));

    checkbox(1).click();
    fixture.detectChanges();

    const submitButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="group-assignment-submit"]',
    );
    submitButton.click();

    expect(emitted?.sort()).toEqual([1, 2]);
  });
});
