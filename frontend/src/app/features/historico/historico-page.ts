import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { BuscaApi } from '../../core/api/busca-api';
import { getApiErrorMessage } from '../../core/api/api-error-message';
import { BuscaResumoResponse } from '../../shared/models/busca.model';
import { CategoriaNegocio } from '../../shared/models/enums.model';

type EstadoHistorico = 'loading' | 'success' | 'empty' | 'error';

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
  imports: [RouterLink],
  selector: 'app-historico-page',
  styleUrl: './historico-page.scss',
  templateUrl: './historico-page.html',
})
export class HistoricoPage {
  private readonly buscaApi = inject(BuscaApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly buscas = signal<BuscaResumoResponse[]>([]);
  protected readonly estado = signal<EstadoHistorico>('loading');
  protected readonly mensagemErro = signal<string | null>(null);

  constructor() {
    this.carregar();
  }

  protected carregar(): void {
    this.estado.set('loading');
    this.mensagemErro.set(null);

    this.buscaApi
      .listarHistorico()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (buscas) => {
          this.buscas.set(buscas);
          this.estado.set(buscas.length === 0 ? 'empty' : 'success');
        },
        error: (error: unknown) => {
          this.mensagemErro.set(getApiErrorMessage(error));
          this.estado.set('error');
        },
      });
  }

  protected formatarDataLocal(data: string): string {
    const correspondencia = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(data);

    if (correspondencia === null) {
      return data;
    }

    const [, ano, mes, dia, hora, minuto] = correspondencia;
    return `${dia}/${mes}/${ano} às ${hora}:${minuto}`;
  }

  protected formatarCategorias(categorias: readonly CategoriaNegocio[]): string {
    return categorias.length > 0
      ? categorias.map((categoria) => ROTULOS_CATEGORIA[categoria]).join(', ')
      : 'Não informadas';
  }

  protected enderecoExibido(endereco: string | null): string {
    return endereco?.trim() || 'Endereço não informado';
  }
}
