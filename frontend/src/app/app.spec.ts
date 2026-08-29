import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes)]
    })
      .compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the accessible shell and its three navigation areas', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    const navigation = compiled.querySelector('nav[aria-label="Navegação principal"]');
    const links = [...compiled.querySelectorAll<HTMLAnchorElement>('.main-navigation__link')];

    expect(navigation).toBeTruthy();
    expect(links.map((link) => link.textContent?.trim())).toEqual(['Busca', 'Kanban', 'Histórico']);
    expect(links.map((link) => link.getAttribute('href'))).toEqual(['/busca', '/kanban', '/historico']);
  });

  it('indica a rota ativa com aria-current', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/kanban');
    await fixture.whenStable();

    const activeLink = fixture.nativeElement.querySelector('.main-navigation__link.is-active');
    expect(activeLink?.textContent?.trim()).toBe('Kanban');
    expect(activeLink?.getAttribute('aria-current')).toBe('page');
  });
});
