import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { EMPTY, expand, Observable, Subject, Subscription, switchMap, timeout, timer } from 'rxjs';
import { BuscaApi } from '../../core/api/busca-api';
import { getApiErrorMessage } from '../../core/api/api-error-message';
import { PesquisaInformacoesExecucaoResponse } from '../../shared/models/busca.model';

/** Estado por detalhe aberto; o trabalho e sua duração pertencem ao servidor. */
@Injectable()
export class PesquisaInformacoesStore {
  private readonly api = inject(BuscaApi);
  private readonly fim = new Subject<void>();
  readonly finalizada = this.fim.asObservable();
  readonly execucao = signal<PesquisaInformacoesExecucaoResponse | null>(null);
  readonly consultando = signal(false);
  readonly iniciando = signal(false);
  readonly conhecida = signal(false);
  readonly usarBrave = signal(true);
  readonly erro = signal<string | null>(null);
  readonly ativa = computed(() => {
    const status = this.execucao()?.status;
    return status === 'PENDENTE' || status === 'EM_ANDAMENTO';
  });
  readonly ocupada = computed(() => this.ativa() || this.iniciando() || this.consultando());
  readonly podeIniciar = computed(() => this.conhecida() && !this.ocupada());
  private buscaId: number | null = null;
  private assinatura?: Subscription;
  private ultimaFinalizada: number | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.assinatura?.unsubscribe();
      this.fim.complete();
    });
  }

  limpar(): void {
    this.assinatura?.unsubscribe();
    this.buscaId = null;
    this.ultimaFinalizada = null;
    this.execucao.set(null);
    this.consultando.set(false);
    this.iniciando.set(false);
    this.conhecida.set(false);
    this.usarBrave.set(true);
    this.erro.set(null);
  }

  acompanhar(id: number): void {
    this.limpar();
    this.buscaId = id;
    this.retomar();
  }

  retomar(): void {
    if (this.buscaId === null || this.consultando() || this.iniciando()) return;
    this.assinatura?.unsubscribe();
    this.consultando.set(true);
    this.erro.set(null);
    this.observar(this.api.consultarInformacoes(this.buscaId));
  }

  iniciar(): void {
    if (this.buscaId === null || !this.podeIniciar()) return;
    this.assinatura?.unsubscribe();
    this.iniciando.set(true);
    this.erro.set(null);
    this.execucao.set(null);
    this.observar(this.api.buscarInformacoes(this.buscaId, this.usarBrave()));
  }

  alternarUsoBrave(): void {
    if (!this.podeIniciar()) return;
    this.usarBrave.update((atual) => !atual);
  }

  private observar(inicial: Observable<PesquisaInformacoesExecucaoResponse | null>): void {
    const id = this.buscaId!;
    this.assinatura = inicial
      .pipe(
        timeout(15000),
        expand((execucao) => {
          if (execucao?.status !== 'PENDENTE' && execucao?.status !== 'EM_ANDAMENTO') return EMPTY;
          // Agenda a próxima consulta somente após a resposta anterior, sem sobreposição.
          return timer(5000).pipe(
            switchMap(() => this.api.consultarInformacoes(id).pipe(timeout(15000))),
          );
        }),
      )
      .subscribe({
        next: (execucao) => {
          this.consultando.set(false);
          this.iniciando.set(false);
          this.conhecida.set(true);
          if (execucao) this.usarBrave.set(execucao.usarBrave);
          this.execucao.set(execucao);
          if (execucao && !this.ativa() && this.ultimaFinalizada !== execucao.id) {
            this.ultimaFinalizada = execucao.id;
            this.fim.next();
          }
        },
        error: (error: unknown) => {
          this.consultando.set(false);
          this.iniciando.set(false);
          this.conhecida.set(false);
          this.erro.set(getApiErrorMessage(error));
        },
      });
  }
}
