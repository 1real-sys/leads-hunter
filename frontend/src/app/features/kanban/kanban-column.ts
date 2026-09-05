import { CdkDrag, CdkDragDrop, CdkDropList } from '@angular/cdk/drag-drop';
import { Component, computed, input, output } from '@angular/core';
import { StatusFunil } from '../../shared/models/enums.model';
import { LeadResponse } from '../../shared/models/lead.model';
import { LeadCard } from './lead-card';
import { ColunaKanban, MudancaStatusLead, PaginaColunaSolicitada } from './kanban.model';

@Component({
  imports: [CdkDrag, CdkDropList, LeadCard],
  selector: 'app-kanban-column',
  styleUrl: './kanban-column.scss',
  templateUrl: './kanban-column.html',
})
export class KanbanColumn {
  readonly coluna = input.required<ColunaKanban>();
  readonly idsEmMovimento = input.required<ReadonlySet<number>>();
  readonly bloqueado = input(false);
  readonly mudancaStatusSolicitada = output<MudancaStatusLead>();
  readonly detalheSolicitado = output<LeadResponse>();
  readonly paginaSolicitada = output<PaginaColunaSolicitada>();
  readonly novaTentativaSolicitada = output<PaginaColunaSolicitada>();

  protected readonly tituloId = computed(() => {
    const status = this.coluna().status.toLowerCase();
    return `kanban-${status}-title`;
  });

  protected readonly aceitaDestino = (
    _drag: CdkDrag<LeadResponse>,
    drop: CdkDropList<ColunaKanban>,
  ): boolean => drop.data.estado !== 'loading';

  protected emMovimento(id: number): boolean {
    return this.idsEmMovimento().has(id);
  }

  protected interacaoDesabilitada(id: number): boolean {
    return this.bloqueado() || this.coluna().estado === 'loading' || this.emMovimento(id);
  }

  protected solicitarMudanca(lead: LeadResponse, status: StatusFunil): void {
    if (!this.interacaoDesabilitada(lead.id) && lead.status !== status) {
      this.mudancaStatusSolicitada.emit({ lead, status });
    }
  }

  protected soltar(event: CdkDragDrop<ColunaKanban, ColunaKanban, LeadResponse>): void {
    const status = event.container.data.status;

    if (
      event.container === event.previousContainer ||
      this.interacaoDesabilitada(event.item.data.id)
    ) {
      return;
    }

    this.solicitarMudanca(event.item.data, status);
  }

  protected solicitarPagina(pagina: number): void {
    const coluna = this.coluna();
    if (
      coluna.estado !== 'loading' &&
      pagina >= 0 &&
      pagina < coluna.totalPaginas &&
      pagina !== coluna.pagina
    ) {
      this.paginaSolicitada.emit({ status: coluna.status, pagina });
    }
  }

  protected tentarNovamente(): void {
    const coluna = this.coluna();
    this.novaTentativaSolicitada.emit({ status: coluna.status, pagina: coluna.pagina });
  }
}
