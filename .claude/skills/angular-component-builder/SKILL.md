---
name: angular-component-builder
description: Use when implementing any new knowly-app component, service, or guard, or extending an existing one. Triggers on "cria uma tela", "cria um componente", "adiciona um guard/service".
---

# angular-component-builder

Concrete scaffolding templates for **knowly-app** (Angular standalone +
signals + zoneless + Vitest). Follow these exact shapes — this is not
React, don't reach for hooks/NgModules/RxJS-as-state-store patterns.

## Rules & anti-patterns

- **DO** use standalone components with an `imports: [...]` array —
  never a NgModule.
- **DO** hold all local/service state in `signal()`/`computed()`; use
  `effect()` only for side effects reacting to signal changes (e.g.
  auto-starting a tour). RxJS stays scoped to actual async streams
  (HTTP calls) — don't use a `BehaviorSubject` where a signal fits.
- **STRICTLY PROHIBITED**: `fakeAsync`/`tick()` in tests — this app is
  zoneless, those silently no-op. Use `vi.useFakeTimers()`.
- **DO** put every HTTP call inside a service, never call `HttpClient`
  directly from a component.
- **DO** gate a service's `fetch()` method to swallow expected failure
  statuses (e.g. a 403 meaning "no permission," not an app error) with
  `catchError` — don't let an expected-outcome HTTP error surface as an
  unhandled console error.
- **STRICTLY PROHIBITED**: deciding a UI/permission state from an
  unrelated list's contents as a proxy (e.g. "does the memberships list
  have entries" as a stand-in for "is this session logged in / does it
  have an active tenant"). A real bug on this project: staff sessions
  never have a real `TenantMembership` row even after switching into a
  tenant, so any such proxy silently breaks for them. Fetch the actual
  signal you need directly.
- **DO** treat a `fetch()`-style method's "no data found" case
  carefully: decide explicitly whether it should null out an existing
  signal value or preserve it — blindly overwriting with `null` broke a
  real feature (`ActiveTenantService#fetch()`) by wiping state a
  different code path had just set correctly.
- **DO** add `data-testid` to every element a test or another
  component's tour/e2e hook needs to find; use `data-tour-id` only for
  onboarding-tour spotlight targets, on the component that's actually
  globally present (not a page-specific one that may not be mounted).

## Execution steps

1. Confirm the PLAN.md's API contract for anything this component/
   service consumes — don't guess a shape.
2. Write the component/service/guard (templates below).
3. Write the Vitest spec **first** if this is new logic (TDAD) —
   `HttpTestingController` for HTTP mocking, `provideRouter([])` for
   routing-adjacent tests, `FakeTranslocoLoader` for i18n.
4. `npm run format && npm run format:check && npm test && npm run build`
   — all four, checking real exit codes (not through a pipe) — before
   considering the task done.

## Templates

Service (state + fetch, mirrors `permissions.service.ts`):

```typescript
@Injectable({ providedIn: 'root' })
export class ExampleService {
  private readonly http = inject(HttpClient);
  private readonly _value = signal<T | null>(null);
  readonly value = this._value.asReadonly();

  fetch(): void {
    this.http.get<Response>('/api/...').pipe(
      catchError(() => of(fallbackValue)),
    ).subscribe((response) => this._value.set(response.value));
  }
}
```

Guard (mirrors `tenant-selection.guard.ts`):

```typescript
export const exampleGuard: CanActivateFn = () => {
  const service = inject(ExampleService);
  const router = inject(Router);

  return service.check().pipe(
    map((ok) => (ok ? true : router.parseUrl('/fallback'))),
  );
};
```

Component test (mirrors `*.component.spec.ts` convention):

```typescript
describe('ExampleComponent', () => {
  let fixture: ComponentFixture<ExampleComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ExampleComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        provideTransloco({ config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' }, loader: FakeTranslocoLoader }),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ExampleComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());
});
```
