import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { describe, expect, it, beforeEach } from 'vitest';
import { BuscaPage } from './features/busca/busca-page';
import { HistoricoPage } from './features/historico/historico-page';
import { KanbanPage } from './features/kanban/kanban-page';
import { NotFoundPage } from './features/not-found/not-found-page';
import { routes } from './app.routes';

describe('rotas do shell', () => {
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes)]
    });
    harness = await RouterTestingHarness.create();
  });

  it('redireciona a raiz para Busca', async () => {
    await harness.navigateByUrl('/');

    expect(TestBed.inject(Router).url).toBe('/busca');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toContain('Busca');
  });

  it('carrega as três áreas por suas URLs', async () => {
    const pages = [
      { url: '/busca', component: BuscaPage, title: 'Busca' },
      { url: '/kanban', component: KanbanPage, title: 'Kanban' },
      { url: '/historico', component: HistoricoPage, title: 'Histórico' }
    ];

    for (const page of pages) {
      const instance = await harness.navigateByUrl(page.url, page.component);

      expect(instance).toBeInstanceOf(page.component);
      expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toContain(page.title);
    }
  });

  it('oferece fallback acessível para rota desconhecida', async () => {
    const instance = await harness.navigateByUrl('/rota-inexistente', NotFoundPage);

    expect(instance).toBeInstanceOf(NotFoundPage);
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent)
      .toContain('Página não encontrada');
    expect(harness.routeNativeElement?.querySelector('a[routerLink="/busca"]')).toBeTruthy();
  });
});
