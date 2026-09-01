import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { BuscaApi } from '../../core/api/busca-api';
import { getApiErrorMessage } from '../../core/api/api-error-message';
import { BuscaRequest, BuscaResponse } from '../../shared/models/busca.model';
import { BuscaForm } from './busca-form';
import { criarBuscaFormInicial } from './busca-form.model';
import { MapaBusca } from './mapa-busca';
import { PontoMapa } from './mapa.model';

type EstadoOperacaoBusca = 'idle' | 'loading' | 'success' | 'empty' | 'error';

@Component({
  imports: [BuscaForm, MapaBusca],
  selector: 'app-busca-page',
  styleUrl: './busca-page.scss',
  templateUrl: './busca-page.html',
})
export class BuscaPage {
  private readonly buscaApi = inject(BuscaApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly buscaModel = signal(criarBuscaFormInicial());
  protected readonly pontoCentral = computed<PontoMapa>(() => ({
    latitude: this.buscaModel().latitude,
    longitude: this.buscaModel().longitude,
  }));
  protected readonly raioKm = computed(() => this.buscaModel().raioKm);
  protected readonly estadoOperacao = signal<EstadoOperacaoBusca>('idle');
  protected readonly carregando = computed(() => this.estadoOperacao() === 'loading');
  protected readonly resultadoBusca = signal<BuscaResponse | null>(null);
  protected readonly mensagemErro = signal<string | null>(null);

  protected atualizarPontoCentral(ponto: PontoMapa): void {
    this.buscaModel.update((modelo) => ({
      ...modelo,
      latitude: ponto.latitude,
      longitude: ponto.longitude,
    }));
  }

  protected executarBusca(request: BuscaRequest): void {
    if (this.carregando()) {
      return;
    }

    this.estadoOperacao.set('loading');
    this.resultadoBusca.set(null);
    this.mensagemErro.set(null);

    this.buscaApi
      .criar(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.resultadoBusca.set(response);
          this.estadoOperacao.set(response.leads.length === 0 ? 'empty' : 'success');
        },
        error: (error: unknown) => {
          this.mensagemErro.set(getApiErrorMessage(error));
          this.estadoOperacao.set('error');
        },
      });
  }
}
