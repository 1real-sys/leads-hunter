import { Component, computed, input, model, output } from '@angular/core';
import {
  CATEGORIAS_NEGOCIO,
  CategoriaNegocio,
  STATUS_FUNIL,
  StatusFunil,
  TEMPERATURAS,
  Temperatura,
} from '../../shared/models/enums.model';
import { ROTULOS_CATEGORIA, ROTULOS_STATUS, ROTULOS_TEMPERATURA } from './kanban.model';

export interface FiltrosLeadForm {
  status: StatusFunil | null;
  categoria: CategoriaNegocio | null;
  temperatura: Temperatura | null;
}

interface OpcaoFiltro<T extends string> {
  valor: T;
  rotulo: string;
}

function criarOpcoes<T extends string>(
  valores: readonly T[],
  rotulos: Readonly<Record<T, string>>,
): readonly OpcaoFiltro<T>[] {
  return valores.map((valor) => ({ valor, rotulo: rotulos[valor] }));
}

function lerOpcao<T extends string>(valor: string, opcoes: readonly T[]): T | null {
  return opcoes.find((opcao) => opcao === valor) ?? null;
}

function valorSelect(event: Event): string {
  return event.target instanceof HTMLSelectElement ? event.target.value : '';
}

@Component({
  selector: 'app-lead-filters',
  styleUrl: './lead-filters.scss',
  templateUrl: './lead-filters.html',
})
export class LeadFilters {
  readonly filtros = model.required<FiltrosLeadForm>();
  readonly carregando = input(false);
  readonly bloqueado = input(false);
  readonly aplicar = output<void>();
  readonly limpar = output<void>();

  protected readonly desabilitado = computed(() => this.carregando() || this.bloqueado());

  protected readonly opcoesStatus = criarOpcoes(STATUS_FUNIL, ROTULOS_STATUS);
  protected readonly opcoesCategoria = criarOpcoes(CATEGORIAS_NEGOCIO, ROTULOS_CATEGORIA);
  protected readonly opcoesTemperatura = criarOpcoes(TEMPERATURAS, ROTULOS_TEMPERATURA);

  protected alterarStatus(event: Event): void {
    const status = lerOpcao(valorSelect(event), STATUS_FUNIL);
    this.filtros.update((filtros) => ({ ...filtros, status }));
  }

  protected alterarCategoria(event: Event): void {
    const categoria = lerOpcao(valorSelect(event), CATEGORIAS_NEGOCIO);
    this.filtros.update((filtros) => ({ ...filtros, categoria }));
  }

  protected alterarTemperatura(event: Event): void {
    const temperatura = lerOpcao(valorSelect(event), TEMPERATURAS);
    this.filtros.update((filtros) => ({ ...filtros, temperatura }));
  }

  protected confirmar(event: SubmitEvent): void {
    event.preventDefault();
    if (!this.desabilitado()) {
      this.aplicar.emit();
    }
  }

  protected limparFiltros(): void {
    if (!this.desabilitado()) {
      this.limpar.emit();
    }
  }
}
