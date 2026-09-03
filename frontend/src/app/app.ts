import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  protected readonly navigationItems = [
    { label: 'Busca', path: '/busca', description: 'Encontrar leads', exact: true },
    { label: 'Kanban', path: '/kanban', description: 'Acompanhar oportunidades', exact: true },
    {
      label: 'Histórico',
      path: '/historico',
      description: 'Revisar buscas anteriores',
      exact: false,
    },
  ] as const;
}
