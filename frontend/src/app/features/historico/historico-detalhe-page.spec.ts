import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_ROUTES } from '../../core/api/api-routes';
import { ApiErrorResponse } from '../../shared/models/api-error-response.model';
import { BuscaDetalheResponse } from '../../shared/models/busca.model';
import { HistoricoDetalhePage } from './historico-detalhe-page';

const DETALHE: BuscaDetalheResponse = {
  id: 42,
  enderecoBase: 'Centro de Vitória',
  latitude: -20.3155,
  longitude: -40.3128,
  raioKm: 5,
  categorias: ['PADARIA', 'FARMACIA'],
  totalEncontrados: 2,
  criadoEm: '2026-09-02T10:30:00',
  leads: [
    {
      id: 8,
      nome: 'Zeta Farmácia',
      categoria: 'FARMACIA',
      enderecoFormatado: 'Rua Sete, 80',
      telefone: '(27) 99999-0000',
      whatsappUrl: 'https://wa.me/5527999990000',
      scoreNaBusca: 62,
      temperaturaNaBusca: 'MORNO',
      status: 'CONTATADO',
      observacoes: 'Retornar na próxima semana.',
      ultimoContatoEm: '2026-09-03T11:45:00',
    },
    {
      id: 7,
      nome: 'Alfa Padaria',
      categoria: 'PADARIA',
      enderecoFormatado: null,
      telefone: null,
      whatsappUrl: null,
      scoreNaBusca: null,
      temperaturaNaBusca: null,
      status: null,
      observacoes: null,
      ultimoContatoEm: null,
    },
  ],
};

describe('HistoricoDetalhePage', () => {
  let harness: RouterTestingHarness;
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'historico/:id', component: HistoricoDetalhePage }]),
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => httpTesting.verify());

  it('consulta somente o detalhe, mostra o resumo e preserva a ordem dos leads da API', async () => {
    const page = await harness.navigateByUrl('/historico/42', HistoricoDetalhePage);
    const request = httpTesting.expectOne(API_ROUTES.busca(42));

    expect(page['estado']()).toBe('loading');
    expect(harness.routeNativeElement?.textContent).toContain('Carregando busca');
    expect(request.request.method).toBe('GET');
    expect(httpTesting.match((req) => req.method !== 'GET')).toHaveLength(0);

    request.flush(DETALHE);
    harness.detectChanges();

    const texto = harness.routeNativeElement?.textContent ?? '';
    const linhas = harness.routeNativeElement?.querySelectorAll('tbody tr') ?? [];
    expect(texto).toContain('02/09/2026 às 10:30');
    expect(texto).toContain('Centro de Vitória');
    expect(texto).toContain('Padaria, Farmácia');
    expect(texto).toContain('-20.3155, -40.3128');
    expect(linhas).toHaveLength(2);
    expect(linhas[0].textContent).toContain('Zeta Farmácia');
    expect(linhas[1].textContent).toContain('Alfa Padaria');
  });

  it('mantém resumo e resultados dentro da região útil do workspace', async () => {
    await harness.navigateByUrl('/historico/42', HistoricoDetalhePage);
    httpTesting.expectOne(API_ROUTES.busca(42)).flush(DETALHE);
    harness.detectChanges();

    const workspace = harness.routeNativeElement?.querySelector('.historico-detalhe__workspace');

    expect(workspace?.querySelector('.historico-detalhe__summary')).not.toBeNull();
    expect(workspace?.querySelector('.historico-detalhe__results')).not.toBeNull();
    expect(harness.routeNativeElement?.querySelector('.historico-detalhe__eyebrow')).toBeNull();
  });

  it('distingue o snapshot da busca dos dados comerciais atuais e omite link inválido', async () => {
    await harness.navigateByUrl('/historico/42', HistoricoDetalhePage);
    httpTesting.expectOne(API_ROUTES.busca(42)).flush(DETALHE);
    harness.detectChanges();

    const linhas = harness.routeNativeElement?.querySelectorAll('tbody tr') ?? [];
    expect(linhas[0].textContent).toContain('Score naquela busca');
    expect(linhas[0].textContent).toContain('62');
    expect(linhas[0].textContent).toContain('Temperatura naquela busca');
    expect(linhas[0].textContent).toContain('Morno');
    expect(linhas[0].textContent).toContain('Status atual');
    expect(linhas[0].textContent).toContain('Contatado');
    expect(linhas[0].textContent).toContain('03/09/2026 às 11:45');
    expect(linhas[0].textContent).toContain('Retornar na próxima semana.');

    const whatsapp = harness.routeNativeElement?.querySelectorAll('a[href^="https://wa.me/"]');
    expect(whatsapp).toHaveLength(1);
    expect(linhas[1].querySelector('a[href^="https://wa.me/"]')).toBeNull();
    expect(linhas[1].textContent).toContain('WhatsApp indisponível');
    expect(linhas[1].textContent).toContain('Não disponível');
    expect(linhas[1].textContent).toContain('Sem etapa');
  });

  it('mantém o resumo e apresenta estado vazio quando a busca não registrou leads', async () => {
    const page = await harness.navigateByUrl('/historico/42', HistoricoDetalhePage);
    httpTesting
      .expectOne(API_ROUTES.busca(42))
      .flush({ ...DETALHE, totalEncontrados: 0, leads: [] });
    harness.detectChanges();

    expect(page['estado']()).toBe('empty');
    expect(harness.routeNativeElement?.textContent).toContain('Resumo da execução');
    expect(harness.routeNativeElement?.textContent).toContain(
      'Nenhum lead foi registrado nesta busca',
    );
    expect(harness.routeNativeElement?.querySelector('table')).toBeNull();
  });

  it('rejeita id inválido no cliente sem enviar requisição', async () => {
    const page = await harness.navigateByUrl('/historico/invalido', HistoricoDetalhePage);

    expect(page['estado']()).toBe('invalid');
    expect(harness.routeNativeElement?.textContent).toContain('Identificador de busca inválido');
    expect(harness.routeNativeElement?.querySelector('a[routerLink="/historico"]')).not.toBeNull();
    expect(httpTesting.match(() => true)).toHaveLength(0);
  });

  it('apresenta retorno seguro ao histórico quando a busca não existe', async () => {
    const page = await harness.navigateByUrl('/historico/999', HistoricoDetalhePage);
    httpTesting.expectOne(API_ROUTES.busca(999)).flush(
      {
        timestamp: '2026-09-03T12:00:00Z',
        status: 404,
        codigo: 'BUSCA_NAO_ENCONTRADA',
        mensagem: 'Busca não encontrada.',
        path: API_ROUTES.busca(999),
      } satisfies ApiErrorResponse,
      { status: 404, statusText: 'Not Found' },
    );
    harness.detectChanges();

    expect(page['estado']()).toBe('not-found');
    expect(harness.routeNativeElement?.textContent).toContain('Busca não encontrada');
    expect(harness.routeNativeElement?.querySelector('a[routerLink="/historico"]')).not.toBeNull();
  });

  it('mostra erro seguro e permite repetir a consulta', async () => {
    const page = await harness.navigateByUrl('/historico/42', HistoricoDetalhePage);
    httpTesting.expectOne(API_ROUTES.busca(42)).flush(
      {
        timestamp: '2026-09-03T12:00:00Z',
        status: 500,
        codigo: 'ERRO_INTERNO',
        mensagem: 'Não foi possível consultar esta busca.',
        path: API_ROUTES.busca(42),
      } satisfies ApiErrorResponse,
      { status: 500, statusText: 'Internal Server Error' },
    );
    harness.detectChanges();

    expect(page['estado']()).toBe('error');
    expect(harness.routeNativeElement?.textContent).toContain(
      'Não foi possível consultar esta busca.',
    );

    const repetir = [...(harness.routeNativeElement?.querySelectorAll('button') ?? [])].find(
      (button) => button.textContent?.trim() === 'Tentar novamente',
    ) as HTMLButtonElement;
    repetir.click();

    const novaConsulta = httpTesting.expectOne(API_ROUTES.busca(42));
    expect(page['estado']()).toBe('loading');
    novaConsulta.flush(DETALHE);
    harness.detectChanges();

    expect(page['estado']()).toBe('success');
  });
});
