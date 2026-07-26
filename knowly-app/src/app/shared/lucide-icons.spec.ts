import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LucideSun } from '@lucide/angular';

// Smoke test for the `@lucide/angular` dependency wiring (primeng-removal task 1).
//
// Deviation from PLAN.md: `@lucide/angular` (the actively-maintained
// successor to the deprecated `lucide-angular` package, and the only one
// with an Angular 22-compatible peer range) does not use a
// `LucideAngularModule.pick({...})` provider — each icon (e.g. `LucideSun`)
// is its own standalone component with an attribute selector
// (`svg[lucideSun]`), imported directly by the components that use it. This
// is tree-shaken by construction (only imported icons end up in the bundle),
// so there is no `app.config.ts` wiring step for this library; task 26
// documents this divergence from PLAN.md.
@Component({
  selector: 'app-lucide-icon-host',
  imports: [LucideSun],
  template: `<svg lucideSun data-testid="icon"></svg>`,
})
class HostComponent {}

describe('@lucide/angular wiring', () => {
  it('renders an imported Lucide icon component as an inline SVG', () => {
    TestBed.configureTestingModule({ imports: [HostComponent] });
    const fixture: ComponentFixture<HostComponent> = TestBed.createComponent(HostComponent);
    fixture.detectChanges();

    const icon = fixture.nativeElement.querySelector('[data-testid="icon"]');
    expect(icon).toBeTruthy();
    expect(icon.tagName.toLowerCase()).toBe('svg');
  });
});
