import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { getApiErrorMessage } from '../../core/api/api-error-message';
import { BuscaApi } from '../../core/api/busca-api';
import { BuscaDetalheResponse } from '../../shared/models/busca.model';
import { CategoriaNegocio, StatusFunil, Temperatura } from '../../shared/models/enums.model';

type EstadoDetalhe = 'loading' | 'success' | 'empty' | 'invalid' | 'not-found' | 'error';

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

const ROTULOS_STATUS: Readonly<Record<StatusFunil, string>> = {
  NOVO: 'Novo',
  QUALIFICADO: 'Qualificado',
  CONTATADO: 'Contatado',
  GANHO: 'Ganho',
  PERDIDO: 'Perdido',
};

const ROTULOS_TEMPERATURA: Readonly<Record<Temperatura, string>> = {
  QUENTE: 'Quente',
  MORNO: 'Morno',
  FRIO: 'Frio',
};

@Component({
  imports: [RouterLink],
  selector: 'app-historico-detalhe-page',
  styleUrl: './historico-detalhe-page.scss',
  templateUrl: './historico-detalhe-page.html',
})
export class HistoricoDetalhePage {
  private readonly buscaApi = inject(BuscaApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);

  protected readonly buscaId = this.route.snapshot.paramMap.get('id') ?? '';
  private readonly buscaIdNumerico = this.obterIdValido(this.buscaId);

  protected readonly detalhe = signal<BuscaDetalheResponse | null>(null);
  protected readonly estado = signal<EstadoDetalhe>(
    this.buscaIdNumerico === null ? 'invalid' : 'loading',
  );
  protected readonly mensagemErro = signal<string | null>(null);

  constructor() {
    if (this.buscaIdNumerico !== null) {
      this.carregar();
    }
  }

  protected carregar(): void {
    if (this.buscaIdNumerico === null) {
      return;
    }

    this.estado.set('loading');
    this.mensagemErro.set(null);

    this.buscaApi
      .buscarHistoricoPorId(this.buscaIdNumerico)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detalhe) => {
          this.detalhe.set(detalhe);
          this.estado.set(detalhe.leads.length === 0 ? 'empty' : 'success');
        },
        error: (error: unknown) => {
          if (error instanceof HttpErrorResponse && error.status === 404) {
            this.estado.set('not-found');
            return;
          }

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

  protected rotuloCategoria(categoria: CategoriaNegocio | null): string {
    return categoria === null ? 'Categoria não informada' : ROTULOS_CATEGORIA[categoria];
  }

  protected rotuloStatus(status: StatusFunil | null): string {
    return status === null ? 'Sem etapa' : ROTULOS_STATUS[status];
  }

  protected rotuloTemperatura(temperatura: Temperatura | null): string {
    return temperatura === null ? 'Não disponível' : ROTULOS_TEMPERATURA[temperatura];
  }

  protected enderecoExibido(endereco: string | null): string {
    return endereco?.trim() || 'Endereço não informado';
  }

  private obterIdValido(valor: string): number | null {
    if (!/^[1-9]\d*$/.test(valor)) {
      return null;
    }

    const id = Number(valor);
    return Number.isSafeInteger(id) ? id : null;
  }
}
