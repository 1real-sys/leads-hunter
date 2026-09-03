import { CdkDropListGroup } from '@angular/cdk/drag-drop';
import { Component, computed, input, output } from '@angular/core';
import { LeadResponse } from '../../shared/models/lead.model';
import { KanbanColumn } from './kanban-column';
import { agruparLeadsPorStatus, MudancaStatusLead } from './kanban.model';

@Component({
  imports: [CdkDropListGroup, KanbanColumn],
  selector: 'app-kanban-board',
  styleUrl: './kanban-board.scss',
  templateUrl: './kanban-board.html',
})
export class KanbanBoard {
  readonly leads = input.required<readonly LeadResponse[]>();
  readonly idsEmMovimento = input<ReadonlySet<number>>(new Set());
  readonly bloqueado = input(false);
  readonly mudancaStatusSolicitada = output<MudancaStatusLead>();

  protected readonly agrupamento = computed(() => agruparLeadsPorStatus(this.leads()));
}
