import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { FiltrosLead, LeadApi } from '../../core/api/lead-api';
import { getApiErrorMessage } from '../../core/api/api-error-message';
import { STATUS_FUNIL } from '../../shared/models/enums.model';
import { LeadResponse } from '../../shared/models/lead.model';
import { KanbanBoard } from './kanban-board';
import { FiltrosLeadForm, LeadFilters } from './lead-filters';
import { MudancaStatusLead, ROTULOS_STATUS } from './kanban.model';

type EstadoConsulta = 'idle' | 'loading' | 'success' | 'empty' | 'error';

const FILTROS_INICIAIS: FiltrosLeadForm = {
  status: null,
  categoria: null,
  temperatura: null,
};

function paraFiltrosApi(filtros: FiltrosLeadForm): FiltrosLead {
  return {
    ...(filtros.status !== null ? { status: filtros.status } : {}),
    ...(filtros.categoria !== null ? { categoria: filtros.categoria } : {}),
    ...(filtros.temperatura !== null ? { temperatura: filtros.temperatura } : {}),
  };
}

@Component({
  imports: [KanbanBoard, LeadFilters],
  selector: 'app-kanban-page',
  styleUrl: './kanban-page.scss',
  templateUrl: './kanban-page.html',
})
export class KanbanPage {
  private readonly leadApi = inject(LeadApi);
  private readonly destroyRef = inject(DestroyRef);
  private filtrosDaUltimaConsulta: FiltrosLead = {};

  protected readonly filtros = signal<FiltrosLeadForm>({ ...FILTROS_INICIAIS });
  protected readonly leads = signal<LeadResponse[]>([]);
  protected readonly estadoConsulta = signal<EstadoConsulta>('idle');
  protected readonly carregando = computed(() => this.estadoConsulta() === 'loading');
  protected readonly mensagemErro = signal<string | null>(null);
  protected readonly filtrosAplicados = signal(false);
  protected readonly idsEmMovimento = signal<ReadonlySet<number>>(new Set());
  protected readonly movimentoEmAndamento = computed(() => this.idsEmMovimento().size > 0);
  protected readonly mensagemMovimento = signal<string | null>(null);
  protected readonly erroMovimento = signal<string | null>(null);

  constructor() {
    this.consultar({});
  }

  protected aplicarFiltros(): void {
    if (this.movimentoEmAndamento()) {
      return;
    }
    this.consultar(paraFiltrosApi(this.filtros()));
  }

  protected limparFiltros(): void {
    if (this.carregando() || this.movimentoEmAndamento()) {
      return;
    }

    this.filtros.set({ ...FILTROS_INICIAIS });
    this.consultar({});
  }

  protected tentarNovamente(): void {
    this.consultar(this.filtrosDaUltimaConsulta);
  }

  protected mudarStatus({ lead, status }: MudancaStatusLead): void {
    const leadAnterior = this.leads().find((item) => item.id === lead.id);

    if (
      leadAnterior === undefined ||
      leadAnterior.status === status ||
      this.idsEmMovimento().has(lead.id) ||
      !STATUS_FUNIL.some((statusPermitido) => statusPermitido === status)
    ) {
      return;
    }

    this.mensagemErro.set(null);
    this.mensagemMovimento.set(null);
    this.erroMovimento.set(null);
    this.estadoConsulta.set('success');
    this.definirMovimento(lead.id, true);
    this.substituirLead({ ...leadAnterior, status });

    this.leadApi
      .atualizar(lead.id, { status })
      .pipe(
        finalize(() => this.definirMovimento(lead.id, false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (leadAtualizado) => {
          this.confirmarMovimento(leadAtualizado);
          const nome = leadAtualizado.nome ?? `Lead ${leadAtualizado.id}`;
          this.mensagemMovimento.set(`${nome} movido para ${ROTULOS_STATUS[status]}.`);
        },
        error: (error: unknown) => {
          this.substituirLead(leadAnterior);
          const nome = leadAnterior.nome ?? `Lead ${leadAnterior.id}`;
          this.erroMovimento.set(`Não foi possível mover ${nome}. ${getApiErrorMessage(error)}`);
        },
      });
  }

  private consultar(filtros: FiltrosLead): void {
    if (this.carregando() || this.movimentoEmAndamento()) {
      return;
    }

    this.filtrosDaUltimaConsulta = filtros;
    this.filtrosAplicados.set(Object.keys(filtros).length > 0);
    this.estadoConsulta.set('loading');
    this.mensagemErro.set(null);
    this.mensagemMovimento.set(null);
    this.erroMovimento.set(null);

    this.leadApi
      .listar(filtros)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (leads) => {
          this.leads.set(leads);
          this.estadoConsulta.set(leads.length === 0 ? 'empty' : 'success');
        },
        error: (error: unknown) => {
          this.mensagemErro.set(getApiErrorMessage(error));
          this.estadoConsulta.set('error');
        },
      });
  }

  private definirMovimento(id: number, ativo: boolean): void {
    this.idsEmMovimento.update((idsAtuais) => {
      const proximosIds = new Set(idsAtuais);
      if (ativo) {
        proximosIds.add(id);
      } else {
        proximosIds.delete(id);
      }
      return proximosIds;
    });
  }

  private substituirLead(leadAtualizado: LeadResponse): void {
    this.leads.update((leads) =>
      leads.map((lead) => (lead.id === leadAtualizado.id ? leadAtualizado : lead)),
    );
  }

  private confirmarMovimento(leadAtualizado: LeadResponse): void {
    const statusFiltrado = this.filtrosDaUltimaConsulta.status;

    if (statusFiltrado !== undefined && leadAtualizado.status !== statusFiltrado) {
      this.leads.update((leads) => leads.filter((lead) => lead.id !== leadAtualizado.id));
    } else {
      this.substituirLead(leadAtualizado);
    }

    this.estadoConsulta.set(this.leads().length === 0 ? 'empty' : 'success');
  }
}
