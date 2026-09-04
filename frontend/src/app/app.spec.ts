import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes)],
    }).compileComponents();
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
    const sidebar = compiled.querySelector('aside.app-sidebar');
    const links = [...compiled.querySelectorAll<HTMLAnchorElement>('.main-navigation__link')];

    expect(sidebar).toBeTruthy();
    expect(navigation).toBeTruthy();
    expect(links.map((link) => link.querySelector('span')?.textContent?.trim())).toEqual([
      'Busca',
      'Kanban',
      'Histórico',
    ]);
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/busca',
      '/kanban',
      '/historico',
    ]);
    expect(compiled.querySelector('header.app-header')).toBeNull();
    expect(compiled.querySelector('footer')).toBeNull();
  });

  it('indica a rota ativa com aria-current', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/kanban');
    await fixture.whenStable();

    const activeLink = fixture.nativeElement.querySelector('.main-navigation__link.is-active');
    expect(activeLink?.querySelector('span')?.textContent?.trim()).toBe('Kanban');
    expect(activeLink?.getAttribute('aria-current')).toBe('page');
  });

  it('mantém Histórico ativo ao navegar para uma busca específica', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/historico/42');
    await fixture.whenStable();

    const activeLink = fixture.nativeElement.querySelector('.main-navigation__link.is-active');
    expect(activeLink?.querySelector('span')?.textContent?.trim()).toBe('Histórico');
    expect(activeLink?.getAttribute('aria-current')).toBe('page');
  });
});
