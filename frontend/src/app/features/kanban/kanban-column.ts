import { CdkDrag, CdkDragDrop, CdkDropList } from '@angular/cdk/drag-drop';
import { Component, computed, input, output } from '@angular/core';
import { StatusFunil } from '../../shared/models/enums.model';
import { LeadResponse } from '../../shared/models/lead.model';
import { LeadCard } from './lead-card';
import { ColunaKanban, MudancaStatusLead } from './kanban.model';

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

  protected readonly tituloId = computed(() => {
    const status = this.coluna().status?.toLowerCase() ?? 'sem-etapa';
    return `kanban-${status}-title`;
  });

  protected readonly aceitaDestino = (
    _drag: CdkDrag<LeadResponse>,
    drop: CdkDropList<ColunaKanban>,
  ): boolean => drop.data.status !== null;

  protected emMovimento(id: number): boolean {
    return this.idsEmMovimento().has(id);
  }

  protected interacaoDesabilitada(id: number): boolean {
    return this.bloqueado() || this.emMovimento(id);
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
      status === null ||
      this.interacaoDesabilitada(event.item.data.id)
    ) {
      return;
    }

    this.solicitarMudanca(event.item.data, status);
  }
}
