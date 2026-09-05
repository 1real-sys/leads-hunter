import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_ROUTES } from '../../core/api/api-routes';
import { ArquivoDownloader } from '../../core/browser/arquivo-downloader';
import { ApiErrorResponse } from '../../shared/models/api-error-response.model';
import { STATUS_FUNIL, StatusFunil } from '../../shared/models/enums.model';
import { LeadResponse, PaginaLeadsResponse } from '../../shared/models/lead.model';
import { KanbanPage } from './kanban-page';

const LEAD_QUENTE: LeadResponse = {
  id: 7,
  googlePlaceId: 'place-7',
  nome: 'Padaria Central',
  categoria: 'PADARIA',
  enderecoFormatado: 'Rua Central, 100',
  telefone: '(27) 3333-4444',
  telefoneNormalizado: '552733334444',
  whatsappUrl: 'https://wa.me/552733334444',
  latitude: -20.3155,
  longitude: -40.3128,
  ratingGoogle: 4.8,
  totalReviews: 120,
  score: 82,
  temperatura: 'QUENTE',
  status: 'QUALIFICADO',
  observacoes: null,
  ultimoContatoEm: null,
  criadoEm: '2026-08-31T10:30:00',
  atualizadoEm: '2026-08-31T10:30:00',
};

const LEAD_MORNO: LeadResponse = {
  ...LEAD_QUENTE,
  id: 8,
  googlePlaceId: 'place-8',
  nome: 'Mercado Bairro',
  categoria: 'MERCADO',
  score: 55,
  temperatura: 'MORNO',
  status: 'NOVO',
};

describe('KanbanPage', () => {
  let httpTesting: HttpTestingController;
  const arquivoDownloader = { baixar: vi.fn() };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      imports: [KanbanPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ArquivoDownloader, useValue: arquivoDownloader },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function criarFixture() {
    const fixture = TestBed.createComponent(KanbanPage);
    await fixture.whenStable();
    return fixture;
  }

  function resposta(
    leads: readonly LeadResponse[],
    pagina = 0,
    totalElementos = leads.length,
    totalPaginas = totalElementos === 0 ? 0 : Math.ceil(totalElementos / 25),
  ): PaginaLeadsResponse {
    return { leads, pagina, tamanho: 25, totalElementos, totalPaginas };
  }

  function requisicaoPagina(
    status: StatusFunil,
    pagina = 0,
    filtros: { categoria?: string; temperatura?: string } = {},
  ) {
    const categoria = filtros.categoria === undefined ? '' : `&categoria=${filtros.categoria}`;
    const temperatura =
      filtros.temperatura === undefined ? '' : `&temperatura=${filtros.temperatura}`;
    return httpTesting.expectOne(
      `${API_ROUTES.leadsPagina}?status=${status}&page=${pagina}&size=25${categoria}${temperatura}`,
    );
  }

  async function concluirCargaInicial(
    fixture: Awaited<ReturnType<typeof criarFixture>>,
    paginas: Partial<Record<StatusFunil, PaginaLeadsResponse>> = {},
  ): Promise<void> {
    for (const status of STATUS_FUNIL) {
      requisicaoPagina(status).flush(paginas[status] ?? resposta([]));
    }
    await fixture.whenStable();
  }

  function selecionar(
    fixture: Awaited<ReturnType<typeof criarFixture>>,
    id: string,
    valor: string,
  ): void {
    const select = fixture.nativeElement.querySelector(id) as HTMLSelectElement;
    select.value = valor;
    select.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function aplicar(fixture: Awaited<ReturnType<typeof criarFixture>>): void {
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new SubmitEvent('submit', { bubbles: true, cancelable: true }));
  }

  function botaoMover(
    fixture: Awaited<ReturnType<typeof criarFixture>>,
    nome: string,
    destino: string,
  ): HTMLButtonElement {
    return fixture.nativeElement.querySelector(
      `[aria-label="Mover ${nome} para ${destino}"]`,
    ) as HTMLButtonElement;
  }

  function botaoDetalhe(
    fixture: Awaited<ReturnType<typeof criarFixture>>,
    nome: string,
  ): HTMLButtonElement {
    return fixture.nativeElement.querySelector(
      `[aria-label="Ver detalhes de ${nome}"]`,
    ) as HTMLButtonElement;
  }

  it('consulta cada status no backend com páginas independentes de no máximo 25 leads', async () => {
    const fixture = await criarFixture();
    const submit = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;

    expect(submit.disabled).toBe(true);
    expect(fixture.nativeElement.querySelectorAll('.kanban-column[aria-busy="true"]')).toHaveLength(
      5,
    );

    await concluirCargaInicial(fixture, {
      NOVO: resposta([LEAD_MORNO], 0, 63, 3),
      QUALIFICADO: resposta([LEAD_QUENTE], 0, 1, 1),
    });

    expect(fixture.nativeElement.querySelectorAll('.lead-card')).toHaveLength(2);
    expect(fixture.nativeElement.textContent).toContain('64 leads no funil');
    expect(
      fixture.nativeElement.querySelector('[data-status="NOVO"] .kanban-column__header span')
        .textContent,
    ).toContain('63');
    expect(fixture.nativeElement.textContent).toContain('1 de 3');
  });

  it('mantém filtros, exportação e quadro nas regiões do workspace operacional', async () => {
    const fixture = await criarFixture();
    await concluirCargaInicial(fixture, { QUALIFICADO: resposta([LEAD_QUENTE]) });

    const operacoes = fixture.nativeElement.querySelector('.kanban-page__operations');
    const workspace = fixture.nativeElement.querySelector('.kanban-page__workspace');

    expect(operacoes?.querySelector('app-lead-filters')).not.toBeNull();
    expect(operacoes?.querySelector('app-exportacao-leads')).not.toBeNull();
    expect(workspace?.querySelector('app-kanban-board')).not.toBeNull();
  });

  it('troca somente a página solicitada e mantém as outras colunas renderizadas', async () => {
    const fixture = await criarFixture();
    await concluirCargaInicial(fixture, {
      NOVO: resposta([LEAD_MORNO], 0, 30, 2),
      QUALIFICADO: resposta([LEAD_QUENTE]),
    });

    const proxima = fixture.nativeElement.querySelector(
      '[data-status="NOVO"] .kanban-column__pagination button:last-child',
    ) as HTMLButtonElement;
    proxima.click();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Padaria Central');
    expect(
      fixture.nativeElement.querySelector('[data-status="NOVO"]')?.getAttribute('aria-busy'),
    ).toBe('true');

    requisicaoPagina('NOVO', 1).flush(resposta([], 1, 30, 2));
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('2 de 2');
  });

  it('preserva filtros na paginação e reinicia a página ao reaplicá-los', async () => {
    const fixture = await criarFixture();
    await concluirCargaInicial(fixture);

    selecionar(fixture, '#lead-status', 'QUALIFICADO');
    selecionar(fixture, '#lead-categoria', 'PADARIA');
    selecionar(fixture, '#lead-temperatura', 'QUENTE');
    aplicar(fixture);

    requisicaoPagina('QUALIFICADO', 0, { categoria: 'PADARIA', temperatura: 'QUENTE' }).flush(
      resposta([LEAD_QUENTE], 0, 27, 2),
    );
    await fixture.whenStable();
    expect(httpTesting.match((request) => request.url === API_ROUTES.leadsPagina)).toHaveLength(0);
    expect(fixture.componentInstance['totalLeads']()).toBe(27);

    const proxima = fixture.nativeElement.querySelector(
      '[data-status="QUALIFICADO"] .kanban-column__pagination button:last-child',
    ) as HTMLButtonElement;
    proxima.click();
    requisicaoPagina('QUALIFICADO', 1, { categoria: 'PADARIA', temperatura: 'QUENTE' }).flush(
      resposta([], 1, 27, 2),
    );
    await fixture.whenStable();

    aplicar(fixture);
    requisicaoPagina('QUALIFICADO', 0, { categoria: 'PADARIA', temperatura: 'QUENTE' }).flush(
      resposta([LEAD_QUENTE], 0, 27, 2),
    );
  });

  it('descarta status manipulado fora dos enums e consulta novamente todas as etapas', async () => {
    const fixture = await criarFixture();
    await concluirCargaInicial(fixture);

    selecionar(fixture, '#lead-status', 'STATUS_INEXISTENTE');
    aplicar(fixture);
    await concluirCargaInicial(fixture);

    expect(fixture.componentInstance['filtros']().status).toBeNull();
  });

  it('limpa filtros e reinicia todas as colunas na primeira página', async () => {
    const fixture = await criarFixture();
    await concluirCargaInicial(fixture);

    selecionar(fixture, '#lead-status', 'QUALIFICADO');
    aplicar(fixture);
    requisicaoPagina('QUALIFICADO').flush(resposta([LEAD_QUENTE]));
    await fixture.whenStable();

    const limpar = [...fixture.nativeElement.querySelectorAll('button')].find(
      (button: HTMLButtonElement) => button.textContent?.trim() === 'Limpar',
    ) as HTMLButtonElement;
    limpar.click();
    await concluirCargaInicial(fixture, { NOVO: resposta([LEAD_MORNO]) });

    expect((fixture.nativeElement.querySelector('#lead-status') as HTMLSelectElement).value).toBe(
      '',
    );
    expect(fixture.componentInstance['filtros']()).toEqual({
      status: null,
      categoria: null,
      temperatura: null,
    });
  });

  it('representa estado vazio na própria coluna sem ocultar o restante do board', async () => {
    const fixture = await criarFixture();
    await concluirCargaInicial(fixture, { NOVO: resposta([LEAD_MORNO]) });

    const perdido = fixture.nativeElement.querySelector('[data-status="PERDIDO"]') as HTMLElement;
    expect(perdido.textContent).toContain('Nenhum lead nesta etapa');
    expect(fixture.nativeElement.textContent).toContain('Mercado Bairro');
    expect(fixture.nativeElement.querySelectorAll('.kanban-column')).toHaveLength(5);
  });

  it('impede nova consulta global enquanto alguma coluna ainda está carregando', async () => {
    const fixture = await criarFixture();
    aplicar(fixture);

    const requisicoes = httpTesting.match((request) => request.url === API_ROUTES.leadsPagina);
    expect(requisicoes).toHaveLength(5);
    for (const request of requisicoes) {
      request.flush(resposta([]));
    }
  });

  it('mantém estados de erro e retry isolados por coluna', async () => {
    const fixture = await criarFixture();
    requisicaoPagina('NOVO').flush(resposta([LEAD_MORNO]));
    requisicaoPagina('QUALIFICADO').flush(
      {
        codigo: 'ERRO_INTERNO',
        mensagem: 'Falha em qualificados.',
        path: API_ROUTES.leadsPagina,
        status: 500,
        timestamp: '2026-09-04T12:00:00Z',
      } satisfies ApiErrorResponse,
      { status: 500, statusText: 'Internal Server Error' },
    );
    for (const status of ['CONTATADO', 'GANHO', 'PERDIDO'] as const) {
      requisicaoPagina(status).flush(resposta([]));
    }
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Mercado Bairro');
    const coluna = fixture.nativeElement.querySelector(
      '[data-status="QUALIFICADO"]',
    ) as HTMLElement;
    expect(coluna.textContent).toContain('Falha em qualificados');
    (coluna.querySelector('button') as HTMLButtonElement).click();

    requisicaoPagina('QUALIFICADO').flush(resposta([LEAD_QUENTE]));
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Padaria Central');
  });

  it('move de forma otimista e reconsulta somente origem e destino após o PATCH', async () => {
    const fixture = await criarFixture();
    await concluirCargaInicial(fixture, { NOVO: resposta([LEAD_MORNO]) });

    botaoMover(fixture, 'Mercado Bairro', 'Qualificado').click();
    await fixture.whenStable();
    expect(
      fixture.nativeElement.querySelector('[data-status="QUALIFICADO"]')?.textContent,
    ).toContain('Mercado Bairro');

    const patch = httpTesting.expectOne(API_ROUTES.lead(8));
    expect(patch.request.body).toEqual({ status: 'QUALIFICADO' });
    const confirmado = {
      ...LEAD_MORNO,
      nome: 'Mercado confirmado',
      status: 'QUALIFICADO' as const,
    };
    patch.flush(confirmado);

    requisicaoPagina('NOVO').flush(resposta([]));
    requisicaoPagina('QUALIFICADO').flush(resposta([confirmado]));
    await fixture.whenStable();

    expect(httpTesting.match((request) => request.url === API_ROUTES.leadsPagina)).toHaveLength(0);
    expect(fixture.nativeElement.textContent).toContain(
      'Mercado confirmado movido para Qualificado',
    );
    expect(fixture.componentInstance['idsEmMovimento']().size).toBe(0);
  });

  it('restaura origem, destino e totais quando o PATCH falha', async () => {
    const fixture = await criarFixture();
    await concluirCargaInicial(fixture, { QUALIFICADO: resposta([LEAD_QUENTE]) });

    botaoMover(fixture, 'Padaria Central', 'Contatado').click();
    httpTesting.expectOne(API_ROUTES.lead(7)).flush(
      {
        codigo: 'ERRO_INTERNO',
        mensagem: 'Não foi possível atualizar o lead.',
        path: API_ROUTES.lead(7),
        status: 500,
        timestamp: '2026-09-04T12:00:00Z',
      } satisfies ApiErrorResponse,
      { status: 500, statusText: 'Internal Server Error' },
    );
    await fixture.whenStable();

    expect(
      fixture.nativeElement.querySelector('[data-status="QUALIFICADO"]')?.textContent,
    ).toContain('Padaria Central');
    expect(fixture.componentInstance['totalLeads']()).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Não foi possível mover Padaria Central');
  });

  it('bloqueia movimentos concorrentes enquanto a primeira persistência está pendente', async () => {
    const fixture = await criarFixture();
    await concluirCargaInicial(fixture, { NOVO: resposta([LEAD_MORNO]) });

    fixture.componentInstance['mudarStatus']({ lead: LEAD_MORNO, status: 'QUALIFICADO' });
    fixture.componentInstance['mudarStatus']({ lead: LEAD_MORNO, status: 'CONTATADO' });

    const patches = httpTesting.match(API_ROUTES.lead(8));
    expect(patches).toHaveLength(1);
    patches[0].flush({ ...LEAD_MORNO, status: 'QUALIFICADO' });
    requisicaoPagina('NOVO').flush(resposta([]));
    requisicaoPagina('QUALIFICADO').flush(resposta([{ ...LEAD_MORNO, status: 'QUALIFICADO' }]));
  });

  it('preserva o filtro de status e reconsulta somente a origem quando o lead sai da visão', async () => {
    const fixture = await criarFixture();
    await concluirCargaInicial(fixture, { NOVO: resposta([LEAD_MORNO]) });

    selecionar(fixture, '#lead-status', 'NOVO');
    aplicar(fixture);
    requisicaoPagina('NOVO').flush(resposta([LEAD_MORNO]));
    await fixture.whenStable();

    botaoMover(fixture, 'Mercado Bairro', 'Qualificado').click();
    httpTesting.expectOne(API_ROUTES.lead(8)).flush({ ...LEAD_MORNO, status: 'QUALIFICADO' });
    requisicaoPagina('NOVO').flush(resposta([]));
    await fixture.whenStable();

    expect(fixture.componentInstance['filtros']().status).toBe('NOVO');
    expect(fixture.componentInstance['totalLeads']()).toBe(0);
    expect(httpTesting.match((request) => request.url === API_ROUTES.leadsPagina)).toHaveLength(0);
  });

  it('abre o detalhe com os dados completos do card selecionado', async () => {
    const fixture = await criarFixture();
    await concluirCargaInicial(fixture, { QUALIFICADO: resposta([LEAD_QUENTE]) });

    botaoDetalhe(fixture, 'Padaria Central').click();
    await fixture.whenStable();

    expect(fixture.componentInstance['leadSelecionado']()).toEqual(LEAD_QUENTE);
    expect(fixture.nativeElement.querySelector('.lead-detalhe-panel')?.textContent).toContain(
      'Abrir conversa no WhatsApp',
    );
  });

  it('não renderiza o detalhe sem um lead selecionado', async () => {
    const fixture = await criarFixture();
    await concluirCargaInicial(fixture);

    expect(fixture.componentInstance['leadSelecionado']()).toBeNull();
    expect(fixture.nativeElement.querySelector('.lead-detalhe-panel')).toBeNull();
  });

  it('fecha o detalhe com Escape e devolve o foco ao botão de origem', async () => {
    const fixture = await criarFixture();
    await concluirCargaInicial(fixture, { QUALIFICADO: resposta([LEAD_QUENTE]) });

    const gatilho = botaoDetalhe(fixture, 'Padaria Central');
    gatilho.focus();
    gatilho.click();
    await fixture.whenStable();

    const painel = fixture.nativeElement.querySelector('.lead-detalhe-panel') as HTMLElement;
    painel.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await fixture.whenStable();

    expect(fixture.componentInstance['leadSelecionado']()).toBeNull();
    expect(document.activeElement).toBe(gatilho);
  });

  it('fecha o detalhe ao acionar o backdrop', async () => {
    const fixture = await criarFixture();
    await concluirCargaInicial(fixture, { NOVO: resposta([LEAD_MORNO]) });

    botaoDetalhe(fixture, 'Mercado Bairro').click();
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('.lead-detalhe-backdrop') as HTMLElement).click();
    await fixture.whenStable();

    expect(fixture.componentInstance['leadSelecionado']()).toBeNull();
  });

  it('atualiza o card visível com a confirmação feita no detalhe', async () => {
    const fixture = await criarFixture();
    await concluirCargaInicial(fixture, { QUALIFICADO: resposta([LEAD_QUENTE]) });

    const detalhe = fixture.nativeElement.querySelector(
      '[aria-label="Ver detalhes de Padaria Central"]',
    ) as HTMLButtonElement;
    detalhe.click();
    await fixture.whenStable();

    const atualizado = { ...LEAD_QUENTE, observacoes: 'Voltou a contatar.' };
    fixture.componentInstance['aplicarLeadAtualizado'](atualizado);
    await fixture.whenStable();

    expect(
      fixture.componentInstance['colunas']().find((coluna) => coluna.status === 'QUALIFICADO')
        ?.leads,
    ).toEqual([atualizado]);
    expect(fixture.componentInstance['leadSelecionado']()).toEqual(atualizado);
  });

  it('exporta com os filtros selecionados sem trocar a paginação do quadro', async () => {
    const fixture = await criarFixture();
    await concluirCargaInicial(fixture);
    selecionar(fixture, '#lead-status', 'QUALIFICADO');
    selecionar(fixture, '#lead-categoria', 'PADARIA');
    selecionar(fixture, '#lead-temperatura', 'QUENTE');
    await fixture.whenStable();

    const exportar = [...fixture.nativeElement.querySelectorAll('button')].find(
      (button: HTMLButtonElement) => button.textContent?.trim() === 'Baixar CSV',
    ) as HTMLButtonElement;
    exportar.click();

    httpTesting
      .expectOne(
        `${API_ROUTES.exportacaoLeadsCsv}?status=QUALIFICADO&categoria=PADARIA&temperatura=QUENTE`,
      )
      .flush(new Blob(['id,nome\r\n']), {
        headers: { 'Content-Disposition': 'attachment; filename="leads.csv"' },
      });
    await fixture.whenStable();

    expect(arquivoDownloader.baixar).toHaveBeenCalledWith(expect.any(Blob), 'leads.csv');
  });
});
