import { CdkDropListGroup } from '@angular/cdk/drag-drop';
import { Component, input, output } from '@angular/core';
import { LeadResponse } from '../../shared/models/lead.model';
import { KanbanColumn } from './kanban-column';
import { ColunaKanban, MudancaStatusLead, PaginaColunaSolicitada } from './kanban.model';

@Component({
  imports: [CdkDropListGroup, KanbanColumn],
  selector: 'app-kanban-board',
  styleUrl: './kanban-board.scss',
  templateUrl: './kanban-board.html',
})
export class KanbanBoard {
  readonly colunas = input.required<readonly ColunaKanban[]>();
  readonly idsEmMovimento = input<ReadonlySet<number>>(new Set());
  readonly bloqueado = input(false);
  readonly mudancaStatusSolicitada = output<MudancaStatusLead>();
  readonly detalheSolicitado = output<LeadResponse>();
  readonly paginaSolicitada = output<PaginaColunaSolicitada>();
  readonly novaTentativaSolicitada = output<PaginaColunaSolicitada>();
}
