import { HttpErrorResponse } from '@angular/common/http';
import {
  afterRenderEffect,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, Subscription } from 'rxjs';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { getApiErrorMessage } from '../../core/api/api-error-message';
import { BuscaApi } from '../../core/api/busca-api';
import { BuscaDetalheResponse } from '../../shared/models/busca.model';
import {
  CategoriaNegocio,
  CnpjOrigem,
  StatusFunil,
  Temperatura,
} from '../../shared/models/enums.model';
import { formatarCnpj } from '../../shared/utils/cnpj';
import { separarObservacoesPesquisa } from '../../shared/utils/observacoes-pesquisa';
import { PesquisaInformacoesStore } from './pesquisa-informacoes-store';
import { BuscaEmailStore } from './busca-email-store';

type EstadoDetalhe = 'loading' | 'success' | 'empty' | 'invalid' | 'not-found' | 'error';

const ROTULOS_CATEGORIA: Readonly<Record<CategoriaNegocio, string>> = {
  MERCADO: 'Mercado',
  PADARIA: 'Padaria',
  DOCERIA: 'Doceria',
  RESTAURANTE: 'Restaurante',
  DISTRIBUIDORA: 'Distribuidora',
  ACOUGUE: 'Açougue',
  FARMACIA: 'Farmácia',
  INFORMATICA: 'Informática',
  VESTUARIO: 'Vestuário',
  PETSHOP: 'Pet Shop / Ração',
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

const ROTULOS_CNPJ_ORIGEM: Readonly<Record<CnpjOrigem, string>> = {
  ENDERECO_EXATO: 'Endereço exato',
  NOME_ENDERECO: 'Nome e endereço',
};

@Component({
  imports: [RouterLink],
  providers: [PesquisaInformacoesStore, BuscaEmailStore],
  selector: 'app-historico-detalhe-page',
  styleUrl: './historico-detalhe-page.scss',
  templateUrl: './historico-detalhe-page.html',
})
export class HistoricoDetalhePage {
  private readonly buscaApi = inject(BuscaApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);

  protected readonly buscaId = signal('');
  private buscaIdNumerico: number | null = null;
  private carregamento?: Subscription;
  private cnpjRequest?: Subscription;
  private acompanhamentoIniciado = false;
  private emailAcompanhamentoIniciado = false;
  protected readonly pesquisa = inject(PesquisaInformacoesStore);
  protected readonly emails = inject(BuscaEmailStore);
  protected readonly erroAtualizacao = signal<string | null>(null);

  protected readonly detalhe = signal<BuscaDetalheResponse | null>(null);
  protected readonly estado = signal<EstadoDetalhe>('loading');
  protected readonly observacoes = computed(
    () =>
      new Map(
        this.detalhe()?.leads.map((lead) => [
          lead.id,
          separarObservacoesPesquisa(lead.observacoes),
        ]),
      ),
  );
  protected readonly mensagemErro = signal<string | null>(null);
  protected readonly buscandoCnpj = signal(false);
  protected readonly mensagemCnpj = signal<string | null>(null);
  protected readonly erroCnpj = signal<string | null>(null);
  private readonly feedbackErro = viewChild<ElementRef<HTMLElement>>('feedbackErro');

  constructor() {
    afterRenderEffect(() => {
      const feedback = this.feedbackErro();
      if (['invalid', 'not-found', 'error'].includes(this.estado()) && feedback) {
        feedback.nativeElement.focus();
      }
    });
    this.pesquisa.finalizada
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.carregar(true));
    this.emails.finalizada
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.carregar(true));
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.carregamento?.unsubscribe();
      this.cnpjRequest?.unsubscribe();
      this.pesquisa.limpar();
      this.emails.limpar();
      this.acompanhamentoIniciado = false;
      this.emailAcompanhamentoIniciado = false;
      this.buscaId.set(params.get('id') ?? '');
      this.buscaIdNumerico = this.obterIdValido(this.buscaId());
      this.detalhe.set(null);
      this.mensagemCnpj.set(null);
      this.erroCnpj.set(null);
      this.erroAtualizacao.set(null);
      this.estado.set(this.buscaIdNumerico === null ? 'invalid' : 'loading');
      this.carregar();
    });
  }

  protected carregar(silencioso = false): void {
    if (this.buscaIdNumerico === null) {
      return;
    }

    this.carregamento?.unsubscribe();
    if (!silencioso) this.estado.set('loading');
    this.mensagemErro.set(null);
    this.erroAtualizacao.set(null);

    this.carregamento = this.buscaApi
      .buscarHistoricoPorId(this.buscaIdNumerico)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detalhe) => {
          this.detalhe.set(detalhe);
          this.estado.set(detalhe.leads.length === 0 ? 'empty' : 'success');
          if (!this.acompanhamentoIniciado) {
            this.acompanhamentoIniciado = true;
            this.pesquisa.acompanhar(detalhe.id);
          }
          if (!this.emailAcompanhamentoIniciado) {
            this.emailAcompanhamentoIniciado = true;
            this.emails.acompanhar(detalhe.id);
          }
        },
        error: (error: unknown) => {
          if (silencioso) {
            this.erroAtualizacao.set(
              'Não foi possível atualizar os dados exibidos. ' + getApiErrorMessage(error),
            );
            return;
          }
          if (error instanceof HttpErrorResponse && error.status === 404) {
            this.estado.set('not-found');
            return;
          }

          this.mensagemErro.set(getApiErrorMessage(error));
          this.estado.set('error');
        },
      });
  }

  protected buscarCnpj(): void {
    if (this.buscaIdNumerico === null || this.buscandoCnpj() || this.estado() !== 'success') {
      return;
    }
    this.buscandoCnpj.set(true);
    this.mensagemCnpj.set(null);
    this.erroCnpj.set(null);
    this.cnpjRequest = this.buscaApi
      .buscarCnpj(this.buscaIdNumerico)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.buscandoCnpj.set(false)),
      )
      .subscribe({
        next: (resumo) => {
          this.mensagemCnpj.set(
            `Consulta concluída: ${resumo.encontrados} encontrados, ` +
              `${resumo.ignoradosJaComCnpj} já com CNPJ e ` +
              `${resumo.semCorrespondencia} sem correspondência, de ${resumo.totalLeads} leads.`,
          );
          this.carregar();
        },
        error: (error: unknown) => this.erroCnpj.set(getApiErrorMessage(error)),
      });
  }

  protected buscarInformacoes(): void {
    if (this.estado() === 'success') this.pesquisa.iniciar();
  }

  protected buscarEmails(): void {
    if (this.estado() === 'success') this.emails.iniciar();
  }

  protected alternarUsoBrave(): void {
    if (this.estado() === 'success') this.pesquisa.alternarUsoBrave();
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

  protected cnpjExibido(cnpj: string | null | undefined): string {
    return formatarCnpj(cnpj) ?? 'CNPJ não encontrado';
  }

  protected rotuloCnpjOrigem(origem: CnpjOrigem | null | undefined): string {
    return origem === undefined || origem === null
      ? 'Origem não informada'
      : ROTULOS_CNPJ_ORIGEM[origem];
  }

  private obterIdValido(valor: string): number | null {
    if (!/^[1-9]\d*$/.test(valor)) {
      return null;
    }

    const id = Number(valor);
    return Number.isSafeInteger(id) ? id : null;
  }
}
