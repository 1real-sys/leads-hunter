import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_ROUTES } from '../../core/api/api-routes';
import { ApiErrorResponse } from '../../shared/models/api-error-response.model';
import { LeadResponse } from '../../shared/models/lead.model';
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

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [KanbanPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function createFixture() {
    const fixture = TestBed.createComponent(KanbanPage);
    await fixture.whenStable();
    return fixture;
  }

  function selecionar(
    fixture: Awaited<ReturnType<typeof createFixture>>,
    id: string,
    value: string,
  ) {
    const select = fixture.nativeElement.querySelector(id) as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function aplicar(fixture: Awaited<ReturnType<typeof createFixture>>): void {
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new SubmitEvent('submit', { bubbles: true, cancelable: true }));
  }

  it('consulta a lista completa ao entrar, representa loading e preserva a ordem da API', async () => {
    const fixture = await createFixture();
    const request = httpTesting.expectOne(API_ROUTES.leads);
    const submit = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;

    expect(request.request.params.keys()).toEqual([]);
    expect(submit.disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Carregando leads');

    request.flush([LEAD_QUENTE, LEAD_MORNO]);
    await fixture.whenStable();

    const cards = fixture.nativeElement.querySelectorAll('.lead-card');
    expect(cards).toHaveLength(2);
    expect(fixture.nativeElement.textContent).toContain('Padaria Central');
    expect(fixture.nativeElement.textContent).toContain('Mercado Bairro');
    expect(fixture.nativeElement.textContent).toContain('2 leads carregados');
    expect(fixture.componentInstance['leads']()).toEqual([LEAD_QUENTE, LEAD_MORNO]);
  });

  it('combina status, categoria e temperatura usando somente enums válidos', async () => {
    const fixture = await createFixture();
    httpTesting.expectOne(API_ROUTES.leads).flush([LEAD_QUENTE, LEAD_MORNO]);
    await fixture.whenStable();

    selecionar(fixture, '#lead-status', 'QUALIFICADO');
    selecionar(fixture, '#lead-categoria', 'PADARIA');
    selecionar(fixture, '#lead-temperatura', 'QUENTE');
    aplicar(fixture);

    const request = httpTesting.expectOne(
      `${API_ROUTES.leads}?status=QUALIFICADO&categoria=PADARIA&temperatura=QUENTE`,
    );
    request.flush([LEAD_QUENTE]);
    await fixture.whenStable();

    expect(fixture.componentInstance['leads']()).toEqual([LEAD_QUENTE]);
    expect(fixture.nativeElement.textContent).toContain('1 lead carregado');
  });

  it('aceita filtros isolados sem produzir os parâmetros não selecionados', async () => {
    const fixture = await createFixture();
    httpTesting.expectOne(API_ROUTES.leads).flush([LEAD_QUENTE]);
    await fixture.whenStable();

    selecionar(fixture, '#lead-categoria', 'PADARIA');
    aplicar(fixture);

    const request = httpTesting.expectOne(`${API_ROUTES.leads}?categoria=PADARIA`);
    expect(request.request.params.keys()).toEqual(['categoria']);
    request.flush([LEAD_QUENTE]);
  });

  it('descarta um valor fora dos enums mesmo se o controle for manipulado', async () => {
    const fixture = await createFixture();
    httpTesting.expectOne(API_ROUTES.leads).flush([LEAD_QUENTE]);
    await fixture.whenStable();

    selecionar(fixture, '#lead-status', 'STATUS_INEXISTENTE');
    aplicar(fixture);

    const request = httpTesting.expectOne(API_ROUTES.leads);
    expect(request.request.params.keys()).toEqual([]);
    request.flush([LEAD_QUENTE]);
    expect(fixture.componentInstance['filtros']().status).toBeNull();
  });

  it('limpa os controles e volta à consulta completa', async () => {
    const fixture = await createFixture();
    httpTesting.expectOne(API_ROUTES.leads).flush([LEAD_QUENTE, LEAD_MORNO]);
    await fixture.whenStable();

    selecionar(fixture, '#lead-status', 'QUALIFICADO');
    aplicar(fixture);
    httpTesting.expectOne(`${API_ROUTES.leads}?status=QUALIFICADO`).flush([LEAD_QUENTE]);
    await fixture.whenStable();

    const clear = [...fixture.nativeElement.querySelectorAll('button')].find(
      (button: HTMLButtonElement) => button.textContent?.trim() === 'Limpar',
    ) as HTMLButtonElement;
    clear.click();
    const request = httpTesting.expectOne(API_ROUTES.leads);
    expect(request.request.params.keys()).toEqual([]);
    request.flush([LEAD_QUENTE, LEAD_MORNO]);
    await fixture.whenStable();

    expect((fixture.nativeElement.querySelector('#lead-status') as HTMLSelectElement).value).toBe(
      '',
    );
    expect(fixture.componentInstance['filtros']()).toEqual({
      status: null,
      categoria: null,
      temperatura: null,
    });
  });

  it('trata resultado vazio e oferece limpar quando existem filtros aplicados', async () => {
    const fixture = await createFixture();
    httpTesting.expectOne(API_ROUTES.leads).flush([LEAD_QUENTE]);
    await fixture.whenStable();

    selecionar(fixture, '#lead-temperatura', 'FRIO');
    aplicar(fixture);
    httpTesting.expectOne(`${API_ROUTES.leads}?temperatura=FRIO`).flush([]);
    await fixture.whenStable();

    expect(fixture.componentInstance['estadoConsulta']()).toBe('empty');
    expect(fixture.nativeElement.textContent).toContain('Nenhum lead encontrado');
    expect(fixture.nativeElement.textContent).toContain('Limpar filtros');
  });

  it('mantém a lista válida anterior quando uma nova consulta falha e permite retry', async () => {
    const fixture = await createFixture();
    httpTesting.expectOne(API_ROUTES.leads).flush([LEAD_QUENTE, LEAD_MORNO]);
    await fixture.whenStable();

    selecionar(fixture, '#lead-status', 'QUALIFICADO');
    aplicar(fixture);
    httpTesting.expectOne(`${API_ROUTES.leads}?status=QUALIFICADO`).flush(
      {
        timestamp: '2026-09-02T12:00:00Z',
        status: 500,
        codigo: 'ERRO_INTERNO',
        mensagem: 'Não foi possível consultar os leads.',
        path: API_ROUTES.leads,
      } satisfies ApiErrorResponse,
      { status: 500, statusText: 'Internal Server Error' },
    );
    await fixture.whenStable();

    expect(fixture.componentInstance['leads']()).toEqual([LEAD_QUENTE, LEAD_MORNO]);
    expect(fixture.nativeElement.textContent).toContain('A lista anterior foi mantida');
    expect(fixture.nativeElement.textContent).toContain('Não foi possível consultar os leads');

    const retry = [...fixture.nativeElement.querySelectorAll('button')].find(
      (button: HTMLButtonElement) => button.textContent?.trim() === 'Tentar novamente',
    ) as HTMLButtonElement;
    retry.click();
    const retryRequest = httpTesting.expectOne(`${API_ROUTES.leads}?status=QUALIFICADO`);
    retryRequest.flush([LEAD_QUENTE]);
    await fixture.whenStable();

    expect(fixture.componentInstance['estadoConsulta']()).toBe('success');
    expect(fixture.componentInstance['leads']()).toEqual([LEAD_QUENTE]);
  });

  it('impede consulta duplicada enquanto a atual está em andamento', async () => {
    const fixture = await createFixture();
    aplicar(fixture);

    const requests = httpTesting.match(API_ROUTES.leads);
    expect(requests).toHaveLength(1);
    requests[0].flush([]);
  });
});
