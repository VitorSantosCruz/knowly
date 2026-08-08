import { TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { TranslocoService } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { PermissionListComponent } from './permission-list.component';
import { PermissionListRow } from './permission-list.model';

describe('PermissionListComponent', () => {
  const rows: PermissionListRow[] = [
    { value: 'STAFF_USER_VIEW', granted: true },
    { value: 'STAFF_USER_CREATE', granted: false },
  ];

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });

    await firstValueFrom(TestBed.inject(TranslocoService).load('en'));
  });

  function create(mode: 'editable' | 'readonly', inputRows: PermissionListRow[] = rows) {
    const fixture = TestBed.createComponent(PermissionListComponent);
    fixture.componentRef.setInput('rows', inputRows);
    fixture.componentRef.setInput('mode', mode);
    fixture.detectChanges();
    return fixture;
  }

  it('renders one row per input permission with name and description text content', () => {
    const fixture = create('readonly');
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('View staff users');
    expect(text).toContain('View the list and detail of staff user accounts.');
    expect(text).toContain('Create staff users');
    expect(text).toContain('Create new staff user accounts.');
  });

  it('renders no role="switch" in readonly mode', () => {
    const fixture = create('readonly');

    expect(fixture.nativeElement.querySelectorAll('[role="switch"]').length).toBe(0);
  });

  it('renders a role="switch" per row in editable mode, reflecting granted state via aria-checked and the row name via aria-label', () => {
    const fixture = create('editable');
    const switches: HTMLElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('[role="switch"]'),
    );

    expect(switches.length).toBe(2);
    expect(switches[0].getAttribute('aria-checked')).toBe('true');
    expect(switches[0].getAttribute('aria-label')).toBe('View staff users');
    expect(switches[1].getAttribute('aria-checked')).toBe('false');
    expect(switches[1].getAttribute('aria-label')).toBe('Create staff users');
  });

  it('emits (toggle) with the row value on click, without mutating rows()', () => {
    const fixture = create('editable');
    const emitted: string[] = [];
    fixture.componentInstance.toggle.subscribe((value: string) => emitted.push(value));

    const switches: HTMLElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('[role="switch"]'),
    );
    switches[0].click();

    expect(emitted).toEqual(['STAFF_USER_VIEW']);
    expect(fixture.componentInstance.rows()).toEqual(rows);
  });

  it('emits (toggle) on Enter and Space keydown', () => {
    const fixture = create('editable');
    const emitted: string[] = [];
    fixture.componentInstance.toggle.subscribe((value: string) => emitted.push(value));

    const switches: HTMLElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('[role="switch"]'),
    );
    switches[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    switches[1].dispatchEvent(new KeyboardEvent('keydown', { key: ' ', bubbles: true }));

    expect(emitted).toEqual(['STAFF_USER_CREATE', 'STAFF_USER_CREATE']);
  });
});
