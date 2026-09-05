import { Component, ElementRef, viewChild } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  private readonly mainContent = viewChild<ElementRef<HTMLElement>>('mainContent');

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

  protected focarConteudo(): void {
    this.mainContent()?.nativeElement.focus();
  }
}
