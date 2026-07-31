import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GradientStatCardComponent } from './gradient-stat-card.component';

@Component({
  selector: 'app-host',
  imports: [GradientStatCardComponent],
  template: `
    <app-gradient-stat-card
      testId="stat-card"
      label="Total tenants"
      subtitle="Companies with an active workspace"
      [value]="12"
      [percentChange]="percentChange"
    >
      <span icon data-testid="stat-card-icon">icon</span>
    </app-gradient-stat-card>
  `,
})
class HostComponent {
  percentChange: number | null | undefined = undefined;
}

describe('GradientStatCardComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HostComponent] });
    fixture = TestBed.createComponent(HostComponent);
  });

  it('renders label, subtitle, value and the icon slot content', () => {
    fixture.detectChanges();

    const el = fixture.nativeElement;
    expect(el.querySelector('[data-testid="stat-card"]').textContent).toContain('Total tenants');
    expect(el.textContent).toContain('Companies with an active workspace');
    expect(el.textContent).toContain('12');
    expect(el.querySelector('[data-testid="stat-card-icon"]')).toBeTruthy();
  });

  it('renders a positive badge with the up sign/color when percentChange is positive', () => {
    fixture.componentInstance.percentChange = 12.5;
    fixture.detectChanges();

    const badge = fixture.nativeElement.querySelector('[data-testid="stat-card-badge"]');
    expect(badge).toBeTruthy();
    expect(badge.textContent).toContain('12.5');
    expect(badge.textContent).toContain('+');
  });

  it('renders a negative badge with the down sign/color when percentChange is negative', () => {
    fixture.componentInstance.percentChange = -8.3;
    fixture.detectChanges();

    const badge = fixture.nativeElement.querySelector('[data-testid="stat-card-badge"]');
    expect(badge).toBeTruthy();
    expect(badge.textContent).toContain('8.3');
    expect(badge.textContent).toContain('-');
  });

  it('renders no badge element when percentChange is undefined', () => {
    fixture.componentInstance.percentChange = undefined;
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="stat-card-badge"]')).toBeFalsy();
  });

  it('renders no badge element when percentChange is null', () => {
    fixture.componentInstance.percentChange = null;
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="stat-card-badge"]')).toBeFalsy();
  });
});
