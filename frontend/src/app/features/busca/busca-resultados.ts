import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BuscaResponse } from '../../shared/models/busca.model';
import { CategoriaNegocio, Temperatura } from '../../shared/models/enums.model';

const ROTULOS_CATEGORIA: Readonly<Record<CategoriaNegocio, string>> = {
  MERCADO: 'Mercado',
  PADARIA: 'Padaria',
  DOCERIA: 'Doceria',
  RESTAURANTE: 'Restaurante',
  DISTRIBUIDORA: 'Distribuidora',
  ACOUGUE: 'Açougue',
  FARMACIA: 'Farmácia',
  OUTROS: 'Outros',
};

@Component({
  imports: [DatePipe, RouterLink],
  selector: 'app-busca-resultados',
  styleUrl: './busca-resultados.scss',
  templateUrl: './busca-resultados.html',
})
export class BuscaResultados {
  readonly resultado = input.required<BuscaResponse>();

  protected rotuloCategoria(categoria: CategoriaNegocio): string {
    return ROTULOS_CATEGORIA[categoria];
  }

  protected rotuloTemperatura(temperatura: Temperatura): string {
    return temperatura === 'QUENTE' ? 'Quente' : temperatura === 'MORNO' ? 'Morno' : 'Frio';
  }
}
