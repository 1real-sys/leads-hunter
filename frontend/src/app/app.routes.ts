import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'busca' },
  {
    path: 'busca',
    title: 'Busca | Leads Hunter',
    loadComponent: () => import('./features/busca/busca-page').then(({ BuscaPage }) => BuscaPage)
  },
  {
    path: 'kanban',
    title: 'Kanban | Leads Hunter',
    loadComponent: () => import('./features/kanban/kanban-page').then(({ KanbanPage }) => KanbanPage)
  },
  {
    path: 'historico',
    title: 'Histórico | Leads Hunter',
    loadComponent: () => import('./features/historico/historico-page').then(({ HistoricoPage }) => HistoricoPage)
  },
  {
    path: '**',
    title: 'Página não encontrada | Leads Hunter',
    loadComponent: () => import('./features/not-found/not-found-page').then(({ NotFoundPage }) => NotFoundPage)
  }
];
