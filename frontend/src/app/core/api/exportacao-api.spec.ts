import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ApiErrorResponse } from '../../shared/models/api-error-response.model';
import { API_ROUTES } from './api-routes';
import { ArquivoExportado, ExportacaoApi } from './exportacao-api';

const CSV_MIME = 'text/csv;charset=UTF-8';
const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

describe('ExportacaoApi', () => {
  let api: ExportacaoApi;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ExportacaoApi);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('baixa CSV como blob com filtros, MIME e nome informados pelo backend', async () => {
    let recebido: ArquivoExportado | undefined;
    api
      .exportarCsv({ status: 'CONTATADO', categoria: 'PADARIA', temperatura: 'QUENTE' })
      .subscribe((arquivo) => (recebido = arquivo));

    const request = httpTesting.expectOne(
      `${API_ROUTES.exportacaoLeadsCsv}?status=CONTATADO&categoria=PADARIA&temperatura=QUENTE`,
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('blob');

    request.flush(new Blob(['id,nome\r\n7,Padaria Central\r\n']), {
      headers: {
        'Content-Disposition': 'attachment; filename="leads-filtrados.csv"',
        'Content-Type': CSV_MIME,
      },
    });

    expect(recebido?.nome).toBe('leads-filtrados.csv');
    expect(recebido?.conteudo.type).toBe(CSV_MIME.toLocaleLowerCase());
    expect(await recebido?.conteudo.text()).toContain('Padaria Central');
  });

  it('baixa XLSX sem filtros e aceita filename UTF-8', () => {
    let recebido: ArquivoExportado | undefined;
    api.exportarXlsx().subscribe((arquivo) => (recebido = arquivo));

    const request = httpTesting.expectOne(API_ROUTES.exportacaoLeadsXlsx);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    expect(request.request.responseType).toBe('blob');

    request.flush(new Blob([new Uint8Array([0x50, 0x4b, 0x03, 0x04])]), {
      headers: {
        'Content-Disposition': "attachment; filename*=UTF-8''prospec%C3%A7%C3%A3o.xlsx",
        'Content-Type': XLSX_MIME,
      },
    });

    expect(recebido?.nome).toBe('prospecção.xlsx');
    expect(recebido?.conteudo.type).toBe(XLSX_MIME);
  });

  it.each([
    ['CSV', () => api.exportarCsv(), API_ROUTES.exportacaoLeadsCsv, 'leads.csv', CSV_MIME],
    ['XLSX', () => api.exportarXlsx(), API_ROUTES.exportacaoLeadsXlsx, 'leads.xlsx', XLSX_MIME],
  ] as const)(
    'usa nome e MIME padrão para %s quando os headers estão ausentes',
    (_formato, exportar, rota, nomeEsperado, mimeEsperado) => {
      let recebido: ArquivoExportado | undefined;
      exportar().subscribe((arquivo) => (recebido = arquivo));

      httpTesting.expectOne(rota).flush(new Blob(['conteúdo']));

      expect(recebido?.nome).toBe(nomeEsperado);
      expect(recebido?.conteudo.type).toBe(mimeEsperado.toLocaleLowerCase());
    },
  );

  it('ignora nome inseguro ou incompatível recebido no header', () => {
    let recebido: ArquivoExportado | undefined;
    api.exportarCsv().subscribe((arquivo) => (recebido = arquivo));

    httpTesting.expectOne(API_ROUTES.exportacaoLeadsCsv).flush(new Blob(['id,nome\r\n']), {
      headers: { 'Content-Disposition': 'attachment; filename="../../planilha.exe"' },
    });

    expect(recebido?.nome).toBe('leads.csv');
  });

  it.each([400, 500])(
    'converte o erro JSON em blob para o contrato seguro no status %i',
    async (status) => {
      const corpo: ApiErrorResponse = {
        timestamp: '2026-09-03T12:00:00Z',
        status,
        codigo: status === 400 ? 'PARAMETRO_INVALIDO' : 'ERRO_INTERNO',
        mensagem: status === 400 ? 'Filtro inválido.' : 'Não foi possível gerar a exportação.',
        path: API_ROUTES.exportacaoLeadsCsv,
      };
      const resultado = firstValueFrom(api.exportarCsv());

      httpTesting
        .expectOne(API_ROUTES.exportacaoLeadsCsv)
        .flush(new Blob([JSON.stringify(corpo)], { type: 'application/json' }), {
          status,
          statusText: status === 400 ? 'Bad Request' : 'Internal Server Error',
          headers: { 'Content-Type': 'application/json' },
        });

      await expect(resultado).rejects.toMatchObject({ status, error: corpo });
    },
  );
});
