import { CdkDragHandle } from '@angular/cdk/drag-drop';
import { Component, computed, input, output } from '@angular/core';
import { StatusFunil } from '../../shared/models/enums.model';
import { LeadResponse } from '../../shared/models/lead.model';
import {
  obterEtapasAdjacentes,
  obterRotuloStatus,
  ROTULOS_CATEGORIA,
  ROTULOS_STATUS,
  ROTULOS_TEMPERATURA,
} from './kanban.model';

@Component({
  imports: [CdkDragHandle],
  selector: 'app-lead-card',
  styleUrl: './lead-card.scss',
  templateUrl: './lead-card.html',
})
export class LeadCard {
  readonly lead = input.required<LeadResponse>();
  readonly movendo = input(false);
  readonly interacaoDesabilitada = input(false);
  readonly mudancaStatusSolicitada = output<StatusFunil>();

  protected readonly tituloId = computed(() => `lead-${this.lead().id}-title`);
  protected readonly rotuloStatus = computed(() => obterRotuloStatus(this.lead().status));
  protected readonly etapasAdjacentes = computed(() => obterEtapasAdjacentes(this.lead().status));
  protected readonly nomeAcessivel = computed(() => this.lead().nome ?? `Lead ${this.lead().id}`);
  protected readonly controlesDesabilitados = computed(
    () => this.movendo() || this.interacaoDesabilitada(),
  );
  protected readonly rotulosCategoria = ROTULOS_CATEGORIA;
  protected readonly rotulosStatus = ROTULOS_STATUS;
  protected readonly rotulosTemperatura = ROTULOS_TEMPERATURA;

  protected solicitarMudanca(status: StatusFunil): void {
    if (!this.controlesDesabilitados()) {
      this.mudancaStatusSolicitada.emit(status);
    }
  }
}
