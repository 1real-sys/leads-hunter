import { Component, signal } from '@angular/core';
import { MapaBusca } from './mapa-busca';
import { PontoMapa } from './mapa.model';

@Component({
  imports: [MapaBusca],
  selector: 'app-busca-page',
  styleUrl: './busca-page.scss',
  templateUrl: './busca-page.html',
})
export class BuscaPage {
  protected readonly pontoCentral = signal<PontoMapa>({
    latitude: -25.4284,
    longitude: -49.2733,
  });
  protected readonly raioKm = signal(5);

  protected atualizarPontoCentral(ponto: PontoMapa): void {
    this.pontoCentral.set(ponto);
  }
}
