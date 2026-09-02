import { Component, computed, input } from '@angular/core';
import { LeadCard } from './lead-card';
import { ColunaKanban } from './kanban.model';

@Component({
  imports: [LeadCard],
  selector: 'app-kanban-column',
  styleUrl: './kanban-column.scss',
  templateUrl: './kanban-column.html',
})
export class KanbanColumn {
  readonly coluna = input.required<ColunaKanban>();

  protected readonly tituloId = computed(() => {
    const status = this.coluna().status?.toLowerCase() ?? 'sem-etapa';
    return `kanban-${status}-title`;
  });
}
