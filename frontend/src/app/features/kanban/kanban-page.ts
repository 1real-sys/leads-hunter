import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { getApiErrorMessage } from '../../core/api/api-error-message';
import { FiltrosLead, LeadApi } from '../../core/api/lead-api';
import { STATUS_FUNIL } from '../../shared/models/enums.model';
import { LeadResponse } from '../../shared/models/lead.model';
import { ExportacaoLeads } from './exportacao-leads';
import { KanbanBoard } from './kanban-board';
import { LeadDetalhe } from './lead-detalhe';
import { FiltrosLeadForm, LeadFilters } from './lead-filters';
import {
  ColunaKanban,
  criarColunasKanban,
  MudancaStatusLead,
  PaginaColunaSolicitada,
  ROTULOS_STATUS,
  TAMANHO_PAGINA_KANBAN,
} from './kanban.model';

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
  imports: [ExportacaoLeads, KanbanBoard, LeadDetalhe, LeadFilters],
  selector: 'app-kanban-page',
  styleUrl: './kanban-page.scss',
  templateUrl: './kanban-page.html',
})
export class KanbanPage {
  private readonly leadApi = inject(LeadApi);
  private readonly destroyRef = inject(DestroyRef);
  private filtrosDaUltimaConsulta: FiltrosLead = {};

  protected readonly filtros = signal<FiltrosLeadForm>({ ...FILTROS_INICIAIS });
  protected readonly filtrosParaExportacao = computed(() => paraFiltrosApi(this.filtros()));
  protected readonly colunas = signal<readonly ColunaKanban[]>(criarColunasKanban());
  protected readonly totalLeads = computed(() =>
    this.colunas().reduce((total, coluna) => total + coluna.totalLeads, 0),
  );
  protected readonly carregando = computed(() =>
    this.colunas().some((coluna) => coluna.estado === 'loading'),
  );
  protected readonly idsEmMovimento = signal<ReadonlySet<number>>(new Set());
  protected readonly movimentoEmAndamento = computed(() => this.idsEmMovimento().size > 0);
  protected readonly mensagemMovimento = signal<string | null>(null);
  protected readonly erroMovimento = signal<string | null>(null);
  protected readonly leadSelecionado = signal<LeadResponse | null>(null);

  private gatilhoDoDetalhe: HTMLElement | null = null;

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

  protected mudarPagina({ status, pagina }: PaginaColunaSolicitada): void {
    const coluna = this.obterColuna(status);
    if (
      coluna === undefined ||
      coluna.estado === 'loading' ||
      pagina < 0 ||
      pagina >= coluna.totalPaginas ||
      pagina === coluna.pagina
    ) {
      return;
    }

    this.carregarColuna(status, pagina, true);
  }

  protected tentarNovamente({ status, pagina }: PaginaColunaSolicitada): void {
    if (this.statusEstaAtivo(status)) {
      this.carregarColuna(status, pagina, true);
    }
  }

  protected mudarStatus({ lead, status }: MudancaStatusLead): void {
    const leadAnterior = this.localizarLead(lead.id);

    if (
      leadAnterior === undefined ||
      leadAnterior.status === null ||
      leadAnterior.status === status ||
      this.movimentoEmAndamento() ||
      !STATUS_FUNIL.some((statusPermitido) => statusPermitido === status)
    ) {
      return;
    }

    const statusAnterior = leadAnterior.status;
    const colunasAnteriores = this.colunas();
    this.mensagemMovimento.set(null);
    this.erroMovimento.set(null);
    this.definirMovimento(lead.id, true);
    this.aplicarMovimentoOtimista(leadAnterior, status);

    this.leadApi
      .atualizar(lead.id, { status })
      .pipe(
        finalize(() => this.definirMovimento(lead.id, false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (leadAtualizado) => {
          this.substituirLead(leadAtualizado);
          if (this.leadSelecionado()?.id === leadAtualizado.id) {
            this.leadSelecionado.set(leadAtualizado);
          }
          const nome = leadAtualizado.nome ?? `Lead ${leadAtualizado.id}`;
          this.mensagemMovimento.set(`${nome} movido para ${ROTULOS_STATUS[status]}.`);
          this.recarregarColunasAfetadas(statusAnterior, status);
        },
        error: (error: unknown) => {
          this.colunas.set(colunasAnteriores);
          const nome = leadAnterior.nome ?? `Lead ${leadAnterior.id}`;
          this.erroMovimento.set(`Não foi possível mover ${nome}. ${getApiErrorMessage(error)}`);
        },
      });
  }

  protected abrirDetalhe(lead: LeadResponse): void {
    const ativo = document.activeElement;
    this.gatilhoDoDetalhe = ativo instanceof HTMLElement ? ativo : null;
    this.leadSelecionado.set(lead);
  }

  protected fecharDetalhe(): void {
    const gatilho = this.gatilhoDoDetalhe;
    this.gatilhoDoDetalhe = null;
    this.leadSelecionado.set(null);
    gatilho?.focus();
  }

  protected aplicarLeadAtualizado(leadAtualizado: LeadResponse): void {
    this.leadSelecionado.set(leadAtualizado);
    this.substituirLead(leadAtualizado);
  }

  private consultar(filtros: FiltrosLead): void {
    if (this.carregando() || this.movimentoEmAndamento()) {
      return;
    }

    this.filtrosDaUltimaConsulta = filtros;
    this.mensagemMovimento.set(null);
    this.erroMovimento.set(null);

    const statusesAtivos = filtros.status === undefined ? STATUS_FUNIL : [filtros.status];
    this.colunas.set(
      criarColunasKanban().map((coluna) => ({
        ...coluna,
        estado: statusesAtivos.includes(coluna.status) ? 'loading' : 'empty',
      })),
    );

    for (const status of statusesAtivos) {
      this.carregarColuna(status, 0, false);
    }
  }

  private carregarColuna(
    status: (typeof STATUS_FUNIL)[number],
    pagina: number,
    preservar: boolean,
  ): void {
    this.atualizarColuna(status, (coluna) => ({
      ...coluna,
      pagina,
      leads: preservar ? coluna.leads : [],
      estado: 'loading',
      mensagemErro: null,
    }));

    this.leadApi
      .listarPagina({
        ...this.filtrosDaUltimaConsulta,
        status,
        page: pagina,
        size: TAMANHO_PAGINA_KANBAN,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (resultado) => {
          if (resultado.totalPaginas > 0 && pagina >= resultado.totalPaginas) {
            this.carregarColuna(status, resultado.totalPaginas - 1, preservar);
            return;
          }

          this.atualizarColuna(status, (coluna) => ({
            ...coluna,
            leads: resultado.leads,
            pagina: resultado.pagina,
            totalPaginas: resultado.totalPaginas,
            totalLeads: resultado.totalElementos,
            estado: resultado.leads.length === 0 ? 'empty' : 'success',
            mensagemErro: null,
          }));
        },
        error: (error: unknown) => {
          this.atualizarColuna(status, (coluna) => ({
            ...coluna,
            estado: 'error',
            mensagemErro: getApiErrorMessage(error),
          }));
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
    this.colunas.update((colunas) =>
      colunas.map((coluna) => ({
        ...coluna,
        leads: coluna.leads.map((lead) => (lead.id === leadAtualizado.id ? leadAtualizado : lead)),
      })),
    );
  }

  private aplicarMovimentoOtimista(
    lead: LeadResponse,
    destino: (typeof STATUS_FUNIL)[number],
  ): void {
    this.colunas.update((colunas) =>
      colunas.map((coluna) => {
        if (coluna.status === lead.status) {
          const totalLeads = Math.max(0, coluna.totalLeads - 1);
          return {
            ...coluna,
            leads: coluna.leads.filter((item) => item.id !== lead.id),
            totalLeads,
            totalPaginas: Math.ceil(totalLeads / TAMANHO_PAGINA_KANBAN),
            estado: totalLeads === 0 ? 'empty' : 'success',
          };
        }

        if (coluna.status === destino && this.statusEstaAtivo(destino)) {
          const leads = this.ordenarELimitarPagina([...coluna.leads, { ...lead, status: destino }]);
          const totalLeads = coluna.totalLeads + 1;
          return {
            ...coluna,
            leads,
            totalLeads,
            totalPaginas: Math.ceil(totalLeads / TAMANHO_PAGINA_KANBAN),
            estado: 'success',
          };
        }

        return coluna;
      }),
    );
  }

  private ordenarELimitarPagina(leads: LeadResponse[]): readonly LeadResponse[] {
    leads.sort((a, b) => {
      const porScore =
        (b.score ?? Number.NEGATIVE_INFINITY) - (a.score ?? Number.NEGATIVE_INFINITY);
      if (porScore !== 0) {
        return porScore;
      }

      const porNome = (a.nome ?? '').localeCompare(b.nome ?? '', 'pt-BR');
      return porNome !== 0 ? porNome : a.id - b.id;
    });

    while (leads.length > TAMANHO_PAGINA_KANBAN) {
      leads.pop();
    }
    return leads;
  }

  private recarregarColunasAfetadas(
    origem: (typeof STATUS_FUNIL)[number],
    destino: (typeof STATUS_FUNIL)[number],
  ): void {
    for (const status of new Set([origem, destino])) {
      if (!this.statusEstaAtivo(status)) {
        continue;
      }
      const coluna = this.obterColuna(status);
      if (coluna !== undefined) {
        const ultimaPagina = Math.max(0, coluna.totalPaginas - 1);
        this.carregarColuna(status, Math.min(coluna.pagina, ultimaPagina), true);
      }
    }
  }

  private statusEstaAtivo(status: (typeof STATUS_FUNIL)[number]): boolean {
    const statusFiltrado = this.filtrosDaUltimaConsulta.status;
    return statusFiltrado === undefined || statusFiltrado === status;
  }

  private localizarLead(id: number): LeadResponse | undefined {
    return this.colunas()
      .flatMap((coluna) => coluna.leads)
      .find((lead) => lead.id === id);
  }

  private obterColuna(status: (typeof STATUS_FUNIL)[number]): ColunaKanban | undefined {
    return this.colunas().find((coluna) => coluna.status === status);
  }

  private atualizarColuna(
    status: (typeof STATUS_FUNIL)[number],
    atualizar: (coluna: ColunaKanban) => ColunaKanban,
  ): void {
    this.colunas.update((colunas) =>
      colunas.map((coluna) => (coluna.status === status ? atualizar(coluna) : coluna)),
    );
  }
}
