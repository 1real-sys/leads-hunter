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
    { label: 'Busca', path: '/busca', description: 'Encontrar leads' },
    { label: 'Kanban', path: '/kanban', description: 'Acompanhar oportunidades' },
    { label: 'Histórico', path: '/historico', description: 'Revisar buscas anteriores' }
  ] as const;
}
