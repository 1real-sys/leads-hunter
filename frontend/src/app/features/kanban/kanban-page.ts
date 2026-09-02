import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FiltrosLead, LeadApi } from '../../core/api/lead-api';
import { getApiErrorMessage } from '../../core/api/api-error-message';
import { LeadResponse } from '../../shared/models/lead.model';
import { KanbanBoard } from './kanban-board';
import { FiltrosLeadForm, LeadFilters } from './lead-filters';

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

  constructor() {
    this.consultar({});
  }

  protected aplicarFiltros(): void {
    this.consultar(paraFiltrosApi(this.filtros()));
  }

  protected limparFiltros(): void {
    if (this.carregando()) {
      return;
    }

    this.filtros.set({ ...FILTROS_INICIAIS });
    this.consultar({});
  }

  protected tentarNovamente(): void {
    this.consultar(this.filtrosDaUltimaConsulta);
  }

  private consultar(filtros: FiltrosLead): void {
    if (this.carregando()) {
      return;
    }

    this.filtrosDaUltimaConsulta = filtros;
    this.filtrosAplicados.set(Object.keys(filtros).length > 0);
    this.estadoConsulta.set('loading');
    this.mensagemErro.set(null);

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
}
