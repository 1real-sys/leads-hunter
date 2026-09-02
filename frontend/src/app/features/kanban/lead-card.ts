import { Component, computed, input } from '@angular/core';
import { LeadResponse } from '../../shared/models/lead.model';
import { obterRotuloStatus, ROTULOS_CATEGORIA, ROTULOS_TEMPERATURA } from './kanban.model';

@Component({
  selector: 'app-lead-card',
  styleUrl: './lead-card.scss',
  templateUrl: './lead-card.html',
})
export class LeadCard {
  readonly lead = input.required<LeadResponse>();

  protected readonly tituloId = computed(() => `lead-${this.lead().id}-title`);
  protected readonly rotuloStatus = computed(() => obterRotuloStatus(this.lead().status));
  protected readonly rotulosCategoria = ROTULOS_CATEGORIA;
  protected readonly rotulosTemperatura = ROTULOS_TEMPERATURA;
}
