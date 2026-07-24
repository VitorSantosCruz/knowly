import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DashboardPageComponent } from './dashboard-page.component';

describe('DashboardPageComponent', () => {
  let fixture: ComponentFixture<DashboardPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardPageComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardPageComponent);
    fixture.detectChanges();
  });

  it('renders the dashboard root', () => {
    expect(fixture.nativeElement.querySelector('[data-testid="dashboard-page"]')).toBeTruthy();
  });
});
