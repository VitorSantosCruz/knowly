import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PeriodFilterComponent } from './period-filter.component';

@Component({
  selector: 'app-host',
  imports: [PeriodFilterComponent],
  template: `<app-period-filter [(period)]="period" />`,
})
class HostComponent {
  readonly period = signal<'7d' | '30d' | '90d' | 'all'>('30d');
}

describe('PeriodFilterComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HostComponent] });
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('renders the four period options', () => {
    const buttons: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll(
      '[data-testid^="period-option-"]',
    );
    expect(buttons).toHaveLength(4);
  });

  it.each([
    ['7d', '7d'],
    ['30d', '30d'],
    ['90d', '90d'],
    ['all', 'all'],
  ])('selecting %s updates the period model', (testId, expected) => {
    const button: HTMLElement = fixture.nativeElement.querySelector(
      `[data-testid="period-option-${testId}"]`,
    );
    button.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.period()).toBe(expected);
  });
});
