import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Subject, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ArquivoExportado, ExportacaoApi } from '../../core/api/exportacao-api';
import { ArquivoDownloader } from '../../core/browser/arquivo-downloader';
import { ApiErrorResponse } from '../../shared/models/api-error-response.model';
import { ExportacaoLeads } from './exportacao-leads';

const ARQUIVO_CSV: ArquivoExportado = {
  conteudo: new Blob(['id,nome\r\n']),
  nome: 'leads.csv',
};

const ARQUIVO_XLSX: ArquivoExportado = {
  conteudo: new Blob([new Uint8Array([0x50, 0x4b, 0x03, 0x04])]),
  nome: 'leads.xlsx',
};

describe('ExportacaoLeads', () => {
  const exportacaoApi = {
    exportarCsv: vi.fn(),
    exportarXlsx: vi.fn(),
  };
  const arquivoDownloader = { baixar: vi.fn() };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      imports: [ExportacaoLeads],
      providers: [
        { provide: ExportacaoApi, useValue: exportacaoApi },
        { provide: ArquivoDownloader, useValue: arquivoDownloader },
      ],
    });
  });

  async function renderizar() {
    const fixture = TestBed.createComponent(ExportacaoLeads);
    fixture.componentRef.setInput('filtros', {
      status: 'CONTATADO',
      categoria: 'PADARIA',
      temperatura: 'QUENTE',
    });
    await fixture.whenStable();
    return fixture;
  }

  function botao(fixture: Awaited<ReturnType<typeof renderizar>>, texto: string) {
    return [...fixture.nativeElement.querySelectorAll('button')].find(
      (item: HTMLButtonElement) => item.textContent?.trim() === texto,
    ) as HTMLButtonElement;
  }

  it('envia os filtros visíveis e inicia o download CSV confirmado pela API', async () => {
    const resposta = new Subject<ArquivoExportado>();
    exportacaoApi.exportarCsv.mockReturnValue(resposta);
    const fixture = await renderizar();

    botao(fixture, 'Baixar CSV').click();
    await fixture.whenStable();

    expect(exportacaoApi.exportarCsv).toHaveBeenCalledWith({
      status: 'CONTATADO',
      categoria: 'PADARIA',
      temperatura: 'QUENTE',
    });
    expect(arquivoDownloader.baixar).not.toHaveBeenCalled();

    resposta.next(ARQUIVO_CSV);
    resposta.complete();
    await fixture.whenStable();

    expect(arquivoDownloader.baixar).toHaveBeenCalledWith(ARQUIVO_CSV.conteudo, ARQUIVO_CSV.nome);
    expect(fixture.nativeElement.textContent).toContain('Download de CSV iniciado.');
  });

  it('bloqueia repetição do mesmo formato e mantém CSV e Excel independentes', async () => {
    const respostaCsv = new Subject<ArquivoExportado>();
    const respostaXlsx = new Subject<ArquivoExportado>();
    exportacaoApi.exportarCsv.mockReturnValue(respostaCsv);
    exportacaoApi.exportarXlsx.mockReturnValue(respostaXlsx);
    const fixture = await renderizar();

    botao(fixture, 'Baixar CSV').click();
    await fixture.whenStable();
    const csvCarregando = botao(fixture, 'Gerando CSV...');
    expect(csvCarregando.disabled).toBe(true);
    csvCarregando.click();
    botao(fixture, 'Baixar Excel').click();
    await fixture.whenStable();

    expect(exportacaoApi.exportarCsv).toHaveBeenCalledOnce();
    expect(exportacaoApi.exportarXlsx).toHaveBeenCalledOnce();
    expect(botao(fixture, 'Gerando Excel...').disabled).toBe(true);

    respostaCsv.next(ARQUIVO_CSV);
    respostaCsv.complete();
    await fixture.whenStable();

    expect(botao(fixture, 'Baixar CSV').disabled).toBe(false);
    expect(botao(fixture, 'Gerando Excel...').disabled).toBe(true);

    respostaXlsx.next(ARQUIVO_XLSX);
    respostaXlsx.complete();
    await fixture.whenStable();

    expect(arquivoDownloader.baixar).toHaveBeenCalledTimes(2);
    expect(botao(fixture, 'Baixar Excel').disabled).toBe(false);
  });

  it.each([
    ['CSV', 'exportarCsv'],
    ['Excel', 'exportarXlsx'],
  ] as const)('mostra erro seguro de %s e não cria download', async (rotulo, metodo) => {
    const corpo: ApiErrorResponse = {
      timestamp: '2026-09-03T12:00:00Z',
      status: 500,
      codigo: 'ERRO_INTERNO',
      mensagem: 'Não foi possível gerar a exportação.',
      path: '/api/exportacao/leads',
    };
    exportacaoApi[metodo].mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            error: corpo,
            status: 500,
            statusText: 'Internal Server Error',
          }),
      ),
    );
    const fixture = await renderizar();

    botao(fixture, rotulo === 'CSV' ? 'Baixar CSV' : 'Baixar Excel').click();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain(
      `${rotulo}: Não foi possível gerar a exportação.`,
    );
    expect(arquivoDownloader.baixar).not.toHaveBeenCalled();
  });

  it('informa falha local e mantém a ação disponível quando o navegador rejeita o download', async () => {
    const resposta = new Subject<ArquivoExportado>();
    exportacaoApi.exportarCsv.mockReturnValue(resposta);
    arquivoDownloader.baixar.mockImplementationOnce(() => {
      throw new Error('Falha simulada.');
    });
    const fixture = await renderizar();

    botao(fixture, 'Baixar CSV').click();
    resposta.next(ARQUIVO_CSV);
    resposta.complete();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain(
      'CSV: Não foi possível iniciar o download. Tente novamente.',
    );
    expect(botao(fixture, 'Baixar CSV').disabled).toBe(false);
  });
});
