import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_ROUTES } from '../../core/api/api-routes';
import { ApiErrorResponse } from '../../shared/models/api-error-response.model';
import {
  BuscaDetalheResponse,
  PesquisaInformacoesExecucaoResponse,
} from '../../shared/models/busca.model';
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
      website: 'https://zetafarmacia.example/',
      cnpj: '12345678000190',
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
      cnpj: null,
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

const EXECUCAO: PesquisaInformacoesExecucaoResponse = {
  id: 90,
  buscaId: 42,
  status: 'EM_ANDAMENTO',
  criadoEm: '2026-09-13T01:00:00',
  iniciadoEm: '2026-09-13T01:00:00',
  atualizadoEm: '2026-09-13T01:00:00',
  terminadoEm: null,
  totalLeads: 2,
  progresso: 0,
  processados: 0,
  ignoradosJaCompletos: 0,
  comInstagram: 0,
  comSite: 0,
  comAmbos: 0,
  semInformacoes: 0,
  falhas: 0,
  erroCodigo: null,
  erroMensagem: null,
};

const OBSERVACOES =
  'Retornar amanhã. <img src=x onerror=alert(1)>\nhttps://manual.example/\n\n' +
  '--- Pesquisa inteligente ---\nInstagram:\nhttps://www.instagram.com/padaria\n\n' +
  'Site próprio:\nhttps://padaria.example/\n--- Fim da pesquisa inteligente ---';

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

  afterEach(() => {
    // Os cenários anteriores à INFO-01.6 não têm pesquisa em andamento.
    httpTesting.match(API_ROUTES.buscaInformacoes(42)).forEach((request) => {
      expect(request.request.method).toBe('GET');
      request.flush(null, { status: 204, statusText: 'No Content' });
    });
    httpTesting.verify();
  });

  function botaoInformacoes(): HTMLButtonElement {
    return harness.routeNativeElement!.querySelector('[data-testid="buscar-informacoes"]')!;
  }

  it('posiciona Buscar informações após CNPJ, aguarda restauração e impede POST duplicado', async () => {
    const page = await harness.navigateByUrl('/historico/42', HistoricoDetalhePage);
    expect(botaoInformacoes().disabled).toBe(true);
    httpTesting.expectOne(API_ROUTES.busca(42)).flush(DETALHE);
    await harness.fixture.whenStable();
    expect(botaoInformacoes().disabled).toBe(true);
    httpTesting
      .expectOne(API_ROUTES.buscaInformacoes(42))
      .flush(null, { status: 204, statusText: 'No Content' });
    await harness.fixture.whenStable();
    expect(
      [...harness.routeNativeElement!.querySelectorAll('.historico-detalhe__actions > *')].map(
        (elemento) => elemento.textContent?.trim(),
      ),
    ).toEqual(['Voltar ao histórico', 'Buscar CNPJ', 'Buscar informações']);
    botaoInformacoes().click();
    page['buscarInformacoes']();
    await harness.fixture.whenStable();
    expect(botaoInformacoes().disabled).toBe(true);
    expect(botaoInformacoes().textContent).toContain('Buscando informações…');
    const request = httpTesting.expectOne(API_ROUTES.buscaInformacoes(42));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();
    request.flush({ ...EXECUCAO, status: 'PENDENTE' });
    await harness.fixture.whenStable();
    expect(harness.routeNativeElement?.textContent).toContain('Aguardando a vez de pesquisar');
    expect(harness.routeNativeElement?.querySelector('[role="status"]')?.textContent).toContain(
      '0 de 2 leads',
    );
  });

  it('restaura execução ativa ao abrir e atualiza observações ao concluir com falha parcial', async () => {
    const page = await harness.navigateByUrl('/historico/42', HistoricoDetalhePage);
    httpTesting.expectOne(API_ROUTES.busca(42)).flush(DETALHE);
    httpTesting.expectOne(API_ROUTES.buscaInformacoes(42)).flush({ ...EXECUCAO, progresso: 1 });
    await harness.fixture.whenStable();
    expect(botaoInformacoes().disabled).toBe(true);
    expect(harness.routeNativeElement?.textContent).toContain('1 de 2 leads');
    page['pesquisa'].retomar();
    httpTesting.expectOne(API_ROUTES.buscaInformacoes(42)).flush({
      ...EXECUCAO,
      status: 'CONCLUIDA_COM_FALHAS',
      progresso: 2,
      processados: 1,
      comInstagram: 1,
      comSite: 1,
      comAmbos: 1,
      falhas: 1,
    });
    httpTesting.expectOne(API_ROUTES.busca(42)).flush({
      ...DETALHE,
      leads: [{ ...DETALHE.leads[0], observacoes: OBSERVACOES }, DETALHE.leads[1]],
    });
    await harness.fixture.whenStable();
    expect(botaoInformacoes().disabled).toBe(false);
    expect(harness.routeNativeElement?.textContent).toContain('Busca de informações concluída');
    expect(harness.routeNativeElement?.textContent).toContain(
      '1 de 2 leads não puderam ser pesquisados',
    );
    expect(harness.routeNativeElement?.textContent).toContain('1 com ambos');
    const observacoes = harness.routeNativeElement!.querySelector(
      '.historico-detalhe__observacoes',
    )!;
    expect(observacoes.textContent).toBe(OBSERVACOES);
    expect(observacoes.querySelectorAll('a')).toHaveLength(2);
    expect(observacoes.querySelector('img')).toBeNull();
    for (const link of observacoes.querySelectorAll('a')) {
      expect(link.target).toBe('_blank');
      expect(link.rel).toBe('noopener noreferrer');
    }
  });

  it.each(['CONCLUIDA', 'FALHA'] as const)(
    'restaura resultado %s ao voltar e permite nova tentativa',
    async (status) => {
      await harness.navigateByUrl('/historico/42', HistoricoDetalhePage);
      httpTesting.expectOne(API_ROUTES.busca(42)).flush(DETALHE);
      httpTesting.expectOne(API_ROUTES.buscaInformacoes(42)).flush({
        ...EXECUCAO,
        status,
        progresso: 2,
        falhas: status === 'FALHA' ? 2 : 0,
        erroMensagem: status === 'FALHA' ? 'A execução foi interrompida.' : null,
      });
      httpTesting.expectOne(API_ROUTES.busca(42)).flush(DETALHE);
      await harness.fixture.whenStable();
      expect(botaoInformacoes().disabled).toBe(false);
      expect(harness.routeNativeElement?.textContent).toContain('Zeta Farmácia');
      if (status === 'FALHA') {
        expect(harness.routeNativeElement?.querySelector('[role="alert"]')?.textContent).toContain(
          'A execução foi interrompida.',
        );
      } else {
        expect(harness.routeNativeElement?.textContent).toContain('Busca de informações concluída');
      }
      botaoInformacoes().click();
      httpTesting.expectOne(API_ROUTES.buscaInformacoes(42)).flush({ ...EXECUCAO, id: 91 });
      await harness.fixture.whenStable();
      expect(harness.routeNativeElement?.querySelector('[role="alert"]')).toBeNull();
    },
  );

  it('falha ao restaurar mantém detalhe e oferece retomar antes de permitir nova pesquisa', async () => {
    await harness.navigateByUrl('/historico/42', HistoricoDetalhePage);
    httpTesting.expectOne(API_ROUTES.busca(42)).flush(DETALHE);
    httpTesting.expectOne(API_ROUTES.buscaInformacoes(42)).error(new ProgressEvent('error'));
    await harness.fixture.whenStable();
    expect(botaoInformacoes().disabled).toBe(true);
    expect(harness.routeNativeElement?.textContent).toContain('Zeta Farmácia');
    const retomar = [...harness.routeNativeElement!.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === 'Retomar acompanhamento',
    )!;
    retomar.click();
    httpTesting.expectOne(API_ROUTES.buscaInformacoes(42)).flush(null);
    await harness.fixture.whenStable();
    expect(botaoInformacoes().disabled).toBe(false);
    expect(harness.routeNativeElement?.querySelector('[role="alert"]')).toBeNull();
  });

  it('falha ao atualizar resultado mantém tabela, informa erro e permite recarregar', async () => {
    await harness.navigateByUrl('/historico/42', HistoricoDetalhePage);
    httpTesting.expectOne(API_ROUTES.busca(42)).flush(DETALHE);
    httpTesting
      .expectOne(API_ROUTES.buscaInformacoes(42))
      .flush({ ...EXECUCAO, status: 'CONCLUIDA' });
    httpTesting.expectOne(API_ROUTES.busca(42)).flush({}, { status: 500, statusText: 'Error' });
    await harness.fixture.whenStable();
    expect(harness.routeNativeElement?.querySelector('table')).not.toBeNull();
    expect(harness.routeNativeElement?.textContent).toContain('Busca de informações concluída');
    expect(harness.routeNativeElement?.querySelector('[role="alert"]')?.textContent).toContain(
      'Não foi possível atualizar',
    );
    [...harness.routeNativeElement!.querySelectorAll('button')]
      .find((button) => button.textContent?.trim() === 'Atualizar dados')!
      .click();
    httpTesting.expectOne(API_ROUTES.busca(42)).flush(DETALHE);
    await harness.fixture.whenStable();
    expect(harness.routeNativeElement?.querySelector('[role="alert"]')).toBeNull();
    httpTesting.expectNone(API_ROUTES.buscaInformacoes(42));
  });

  it('troca de id na mesma rota cancela acompanhamento e não exibe dados da busca anterior', async () => {
    await harness.navigateByUrl('/historico/42', HistoricoDetalhePage);
    httpTesting.expectOne(API_ROUTES.busca(42)).flush(DETALHE);
    const consultaAntiga = httpTesting.expectOne(API_ROUTES.buscaInformacoes(42));
    await harness.navigateByUrl('/historico/43', HistoricoDetalhePage);
    expect(consultaAntiga.cancelled).toBe(true);
    expect(harness.routeNativeElement?.textContent).not.toContain('Zeta Farmácia');
    httpTesting.expectOne(API_ROUTES.busca(43)).flush({ ...DETALHE, id: 43 });
    httpTesting.expectOne(API_ROUTES.buscaInformacoes(43)).flush(null);
    await harness.fixture.whenStable();
    expect(harness.routeNativeElement?.textContent).toContain('Busca #43');
    expect(botaoInformacoes().disabled).toBe(false);
  });

  it('detalhe vazio ou inválido nunca permite iniciar pesquisa', async () => {
    const page = await harness.navigateByUrl('/historico/42', HistoricoDetalhePage);
    httpTesting.expectOne(API_ROUTES.busca(42)).flush({ ...DETALHE, leads: [] });
    httpTesting.expectOne(API_ROUTES.buscaInformacoes(42)).flush(null);
    await harness.fixture.whenStable();
    expect(botaoInformacoes().disabled).toBe(true);
    page['buscarInformacoes']();
    httpTesting.expectNone(API_ROUTES.buscaInformacoes(42));
    await harness.navigateByUrl('/historico/invalido', HistoricoDetalhePage);
    expect(botaoInformacoes().disabled).toBe(true);
    httpTesting.expectNone(() => true);
  });

  it('busca CNPJ pelo botão à direita do voltar, bloqueia duplicatas e recarrega os dados', async () => {
    const page = await harness.navigateByUrl('/historico/42', HistoricoDetalhePage);
    httpTesting.expectOne(API_ROUTES.busca(42)).flush(DETALHE);
    await harness.fixture.whenStable();
    const voltar = harness.routeNativeElement!.querySelector('a[routerLink="/historico"]')!;
    const botao = voltar.nextElementSibling as HTMLButtonElement;
    expect(botao.textContent?.trim()).toBe('Buscar CNPJ');
    botao.click();
    await harness.fixture.whenStable();
    expect(botao.disabled).toBe(true);
    expect(botao.textContent).toContain('Buscando CNPJ');
    page['buscarCnpj']();
    const request = httpTesting.expectOne(`${API_ROUTES.busca(42)}/cnpj`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();
    request.flush({ totalLeads: 2, ignoradosJaComCnpj: 1, encontrados: 1, semCorrespondencia: 0 });
    await harness.fixture.whenStable();
    expect(botao.disabled).toBe(true);
    httpTesting.expectOne(API_ROUTES.busca(42)).flush({
      ...DETALHE,
      leads: [
        DETALHE.leads[0],
        { ...DETALHE.leads[1], cnpj: '43869215000156', razaoSocial: 'Empresa encontrada' },
      ],
    });
    await harness.fixture.whenStable();
    expect(botao.disabled).toBe(false);
    expect(harness.routeNativeElement?.textContent).toContain('1 encontrados');
    expect(harness.routeNativeElement?.textContent).toContain('43.869.215/0001-56');
    expect(harness.routeNativeElement?.textContent).toContain('Razão social: Empresa encontrada');
  });

  it('mostra erro da correspondência sem perder o detalhe e permite nova tentativa', async () => {
    await harness.navigateByUrl('/historico/42', HistoricoDetalhePage);
    httpTesting.expectOne(API_ROUTES.busca(42)).flush(DETALHE);
    await harness.fixture.whenStable();
    const botao = harness.routeNativeElement!.querySelector(
      '.historico-detalhe__actions button',
    ) as HTMLButtonElement;
    botao.click();
    httpTesting
      .expectOne(`${API_ROUTES.busca(42)}/cnpj`)
      .flush(
        { mensagem: 'Não foi possível buscar CNPJ.' },
        { status: 500, statusText: 'Internal Server Error' },
      );
    await harness.fixture.whenStable();
    expect(botao.disabled).toBe(false);
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')?.textContent).toContain(
      'Ocorreu um erro interno. Tente novamente mais tarde.',
    );
    expect(harness.routeNativeElement?.textContent).toContain('Zeta Farmácia');
    httpTesting.expectNone(API_ROUTES.busca(42));
    botao.click();
    httpTesting.expectOne(`${API_ROUTES.busca(42)}/cnpj`).flush({
      totalLeads: 2,
      ignoradosJaComCnpj: 1,
      encontrados: 0,
      semCorrespondencia: 1,
    });
    httpTesting.expectOne(API_ROUTES.busca(42)).flush(DETALHE);
    await harness.fixture.whenStable();
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')).toBeNull();
    expect(harness.routeNativeElement?.textContent).toContain('0 encontrados');
    expect(harness.routeNativeElement?.textContent).toContain('1 sem correspondência');
  });

  it('distingue sucesso da correspondência de falha ao recarregar o detalhe', async () => {
    const page = await harness.navigateByUrl('/historico/42', HistoricoDetalhePage);
    httpTesting.expectOne(API_ROUTES.busca(42)).flush(DETALHE);
    page['buscarCnpj']();
    httpTesting.expectOne(`${API_ROUTES.busca(42)}/cnpj`).flush({
      totalLeads: 2,
      ignoradosJaComCnpj: 1,
      encontrados: 1,
      semCorrespondencia: 0,
    });
    httpTesting.expectOne(API_ROUTES.busca(42)).flush({}, { status: 500, statusText: 'Error' });
    await harness.fixture.whenStable();
    expect(harness.routeNativeElement?.textContent).toContain('Consulta concluída');
    expect(harness.routeNativeElement?.textContent).toContain(
      'Não foi possível carregar esta busca',
    );
    expect(page['buscandoCnpj']()).toBe(false);
  });

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
    expect(linhas[0].textContent).toContain('12.345.678/0001-90');
    expect(linhas[1].textContent).toContain('Alfa Padaria');
    expect(linhas[1].textContent).toContain('CNPJ não encontrado');
  });

  it('aceita resposta antiga sem o campo CNPJ e apresenta ausência neutra', async () => {
    const leadSemCnpj = { ...DETALHE.leads[0] };
    delete leadSemCnpj.cnpj;

    const page = await harness.navigateByUrl('/historico/42', HistoricoDetalhePage);
    httpTesting
      .expectOne(API_ROUTES.busca(42))
      .flush({ ...DETALHE, totalEncontrados: 1, leads: [leadSemCnpj] });
    harness.detectChanges();

    expect(page['estado']()).toBe('success');
    expect(harness.routeNativeElement?.textContent).toContain('CNPJ não encontrado');
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
