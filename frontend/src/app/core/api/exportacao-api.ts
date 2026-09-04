import { HttpClient, HttpErrorResponse, HttpParams, HttpResponse } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { catchError, from, map, mergeMap, Observable, throwError } from 'rxjs';
import { FiltrosLead } from './lead-api';
import { API_ROUTES } from './api-routes';

export interface ArquivoExportado {
  conteudo: Blob;
  nome: string;
}

interface ConfiguracaoExportacao {
  rota: string;
  nomePadrao: string;
  extensao: string;
  tipoMimePadrao: string;
}

const CONFIGURACAO_CSV: ConfiguracaoExportacao = {
  rota: API_ROUTES.exportacaoLeadsCsv,
  nomePadrao: 'leads.csv',
  extensao: '.csv',
  tipoMimePadrao: 'text/csv;charset=UTF-8',
};

const CONFIGURACAO_XLSX: ConfiguracaoExportacao = {
  rota: API_ROUTES.exportacaoLeadsXlsx,
  nomePadrao: 'leads.xlsx',
  extensao: '.xlsx',
  tipoMimePadrao: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
};

const TAMANHO_MAXIMO_ERRO_JSON = 1_000_000;

function criarParametros(filtros: FiltrosLead): HttpParams {
  let params = new HttpParams();

  if (filtros.status !== undefined) {
    params = params.set('status', filtros.status);
  }
  if (filtros.categoria !== undefined) {
    params = params.set('categoria', filtros.categoria);
  }
  if (filtros.temperatura !== undefined) {
    params = params.set('temperatura', filtros.temperatura);
  }

  return params;
}

function decodificarNome(valor: string): string {
  const semAspas = valor.trim().replace(/^"|"$/g, '');

  try {
    return decodeURIComponent(semAspas);
  } catch {
    return semAspas;
  }
}

function nomeDoContentDisposition(contentDisposition: string | null): string | null {
  if (contentDisposition === null) {
    return null;
  }

  const filenameEstendido = /filename\*\s*=\s*(?:UTF-8'')?("[^"]*"|[^;]+)/i.exec(
    contentDisposition,
  );
  if (filenameEstendido !== null) {
    return decodificarNome(filenameEstendido[1]);
  }

  const filename = /filename\s*=\s*("[^"]*"|[^;]+)/i.exec(contentDisposition);
  return filename === null ? null : decodificarNome(filename[1]);
}

function nomeSeguro(
  contentDisposition: string | null,
  configuracao: ConfiguracaoExportacao,
): string {
  const nomeRecebido = nomeDoContentDisposition(contentDisposition);
  if (nomeRecebido === null) {
    return configuracao.nomePadrao;
  }

  const ultimoSegmento = nomeRecebido.replaceAll('\\', '/').split('/').at(-1) ?? '';
  const nome = ultimoSegmento.replace(/[\u0000-\u001f\u007f]/g, '').trim();

  if (
    nome.length === 0 ||
    nome.length > 180 ||
    !nome.toLocaleLowerCase().endsWith(configuracao.extensao)
  ) {
    return configuracao.nomePadrao;
  }

  return nome;
}

@Service()
export class ExportacaoApi {
  private readonly http = inject(HttpClient);

  exportarCsv(filtros: FiltrosLead = {}): Observable<ArquivoExportado> {
    return this.exportar(CONFIGURACAO_CSV, filtros);
  }

  exportarXlsx(filtros: FiltrosLead = {}): Observable<ArquivoExportado> {
    return this.exportar(CONFIGURACAO_XLSX, filtros);
  }

  private exportar(
    configuracao: ConfiguracaoExportacao,
    filtros: FiltrosLead,
  ): Observable<ArquivoExportado> {
    return this.http
      .get(configuracao.rota, {
        observe: 'response',
        params: criarParametros(filtros),
        responseType: 'blob',
      })
      .pipe(
        map((response) => this.criarArquivo(response, configuracao)),
        catchError((error: unknown) => this.propagarErro(error)),
      );
  }

  private criarArquivo(
    response: HttpResponse<Blob>,
    configuracao: ConfiguracaoExportacao,
  ): ArquivoExportado {
    if (response.body === null) {
      throw new Error('Resposta de exportação sem conteúdo.');
    }

    const tipoMime = response.headers.get('Content-Type') ?? configuracao.tipoMimePadrao;

    return {
      conteudo: response.body.slice(0, response.body.size, tipoMime),
      nome: nomeSeguro(response.headers.get('Content-Disposition'), configuracao),
    };
  }

  private propagarErro(error: unknown): Observable<never> {
    if (
      !(error instanceof HttpErrorResponse) ||
      !(error.error instanceof Blob) ||
      error.error.size > TAMANHO_MAXIMO_ERRO_JSON
    ) {
      return throwError(() => error);
    }

    return from(this.converterErroJson(error)).pipe(
      mergeMap((erroConvertido) => throwError(() => erroConvertido)),
    );
  }

  private async converterErroJson(error: HttpErrorResponse): Promise<HttpErrorResponse> {
    try {
      const corpo = JSON.parse(await error.error.text()) as unknown;
      return new HttpErrorResponse({
        error: corpo,
        headers: error.headers,
        status: error.status,
        statusText: error.statusText,
        url: error.url ?? undefined,
      });
    } catch {
      return error;
    }
  }
}
