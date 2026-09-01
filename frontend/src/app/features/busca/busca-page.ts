import { Component, computed, signal } from '@angular/core';
import { BuscaForm } from './busca-form';
import { criarBuscaFormInicial } from './busca-form.model';
import { MapaBusca } from './mapa-busca';
import { PontoMapa } from './mapa.model';

@Component({
  imports: [BuscaForm, MapaBusca],
  selector: 'app-busca-page',
  styleUrl: './busca-page.scss',
  templateUrl: './busca-page.html',
})
export class BuscaPage {
  protected readonly buscaModel = signal(criarBuscaFormInicial());
  protected readonly pontoCentral = computed<PontoMapa>(() => ({
    latitude: this.buscaModel().latitude,
    longitude: this.buscaModel().longitude,
  }));
  protected readonly raioKm = computed(() => this.buscaModel().raioKm);

  protected atualizarPontoCentral(ponto: PontoMapa): void {
    this.buscaModel.update((modelo) => ({
      ...modelo,
      latitude: ponto.latitude,
      longitude: ponto.longitude,
    }));
  }
}
