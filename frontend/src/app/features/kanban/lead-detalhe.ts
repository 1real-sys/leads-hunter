import { CdkTrapFocus } from '@angular/cdk/a11y';
import { DatePipe } from '@angular/common';
import {
  afterNextRender,
  Component,
  computed,
  ElementRef,
  input,
  output,
  viewChild,
} from '@angular/core';
import { LeadResponse } from '../../shared/models/lead.model';
import { ROTULOS_CATEGORIA, ROTULOS_TEMPERATURA, obterRotuloStatus } from './kanban.model';

@Component({
  imports: [CdkTrapFocus, DatePipe],
  selector: 'app-lead-detalhe',
  styleUrl: './lead-detalhe.scss',
  templateUrl: './lead-detalhe.html',
})
export class LeadDetalhe {
  readonly lead = input.required<LeadResponse>();
  readonly fechado = output<void>();

  private readonly botaoFechar = viewChild<ElementRef<HTMLButtonElement>>('botaoFechar');

  protected readonly tituloId = computed(() => `detalhe-lead-${this.lead().id}-titulo`);
  protected readonly rotuloStatus = computed(() => obterRotuloStatus(this.lead().status));
  protected readonly rotulosCategoria = ROTULOS_CATEGORIA;
  protected readonly rotulosTemperatura = ROTULOS_TEMPERATURA;

  constructor() {
    afterNextRender(() => this.botaoFechar()?.nativeElement.focus());
  }

  protected fechar(): void {
    this.fechado.emit();
  }
}
