import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { afterEach, vi } from 'vitest';
import { App } from './app';
import { TEMA_STORAGE_KEY } from './core/theme/tema.model';
import { routes } from './app.routes';

describe('App', () => {
  let storage: Storage;

  beforeEach(async () => {
    const values = new Map<string, string>();
    storage = {
      get length() {
        return values.size;
      },
      clear: vi.fn(() => values.clear()),
      getItem: vi.fn((key: string) => values.get(key) ?? null),
      key: vi.fn((index: number) => [...values.keys()][index] ?? null),
      removeItem: vi.fn((key: string) => values.delete(key)),
      setItem: vi.fn((key: string, value: string) => values.set(key, value)),
    };
    vi.stubGlobal('localStorage', storage);
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.style.colorScheme = '';

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes)],
    }).compileComponents();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.style.colorScheme = '';
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the accessible shell and its four navigation areas', async () => {
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
      'Bloqueios',
    ]);
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/busca',
      '/kanban',
      '/historico',
      '/bloqueios',
    ]);
    expect(compiled.querySelector('header.app-header')).toBeNull();
    expect(compiled.querySelector('footer')).toBeNull();
  });

  it('oferece as preferências de tema com rótulo visível', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    const label = compiled.querySelector<HTMLLabelElement>('label[for="theme-preference"]');
    const select = compiled.querySelector<HTMLSelectElement>('#theme-preference');
    const options = [...(select?.options ?? [])];

    expect(label?.textContent?.trim()).toBe('Tema');
    expect(options.map((option) => option.value)).toEqual(['system', 'light', 'dark']);
    expect(options.map((option) => option.textContent?.trim())).toEqual([
      'Sistema',
      'Claro',
      'Escuro',
    ]);
    expect(select?.value).toBe('system');
  });

  it('aplica e persiste a preferência escolhida', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const select = (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>(
      '#theme-preference',
    );

    expect(select).toBeTruthy();
    select!.value = 'dark';
    select!.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    expect(storage.getItem(TEMA_STORAGE_KEY)).toBe('dark');
    expect(document.documentElement.dataset['theme']).toBe('dark');
    expect(document.documentElement.style.colorScheme).toBe('dark');
  });

  it('reflete no seletor a preferência restaurada do storage', async () => {
    storage.setItem(TEMA_STORAGE_KEY, 'dark');

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const select = (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>(
      '#theme-preference',
    );

    expect(select?.value).toBe('dark');
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
