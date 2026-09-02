import { Component, computed, input } from '@angular/core';
import { LeadResponse } from '../../shared/models/lead.model';
import { KanbanColumn } from './kanban-column';
import { agruparLeadsPorStatus } from './kanban.model';

@Component({
  imports: [KanbanColumn],
  selector: 'app-kanban-board',
  styleUrl: './kanban-board.scss',
  templateUrl: './kanban-board.html',
})
export class KanbanBoard {
  readonly leads = input.required<readonly LeadResponse[]>();

  protected readonly agrupamento = computed(() => agruparLeadsPorStatus(this.leads()));
}
