import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_ROUTES } from '../../core/api/api-routes';
import { ApiErrorResponse } from '../../shared/models/api-error-response.model';
import { BuscaResumoResponse } from '../../shared/models/busca.model';
import { HistoricoDetalhePage } from './historico-detalhe-page';
import { HistoricoPage } from './historico-page';

const HISTORICO: BuscaResumoResponse[] = [
  {
    id: 43,
    enderecoBase: 'Praia do Canto, Vitória',
    latitude: -20.2995,
    longitude: -40.2924,
    raioKm: 3,
    categorias: ['RESTAURANTE'],
    totalEncontrados: 8,
    criadoEm: '2026-09-03T09:15:00',
  },
  {
    id: 42,
    enderecoBase: null,
    latitude: -20.3155,
    longitude: -40.3128,
    raioKm: 5,
    categorias: ['PADARIA', 'FARMACIA'],
    totalEncontrados: 18,
    criadoEm: '2026-09-02T10:30:00',
  },
];

describe('HistoricoPage', () => {
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HistoricoPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'historico/:id', component: HistoricoDetalhePage }]),
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function renderizar() {
    const fixture = TestBed.createComponent(HistoricoPage);
    await fixture.whenStable();
    return fixture;
  }

  it('consulta somente o GET do histórico, representa loading e preserva a ordem da API', async () => {
    const fixture = await renderizar();
    const request = httpTesting.expectOne(API_ROUTES.buscas);

    expect(request.request.method).toBe('GET');
    expect(fixture.nativeElement.textContent).toContain('Carregando histórico');
    expect(httpTesting.match((req) => req.method === 'POST')).toHaveLength(0);

    request.flush(HISTORICO);
    await fixture.whenStable();

    const linhas = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(linhas).toHaveLength(2);
    expect(linhas[0].textContent).toContain('Praia do Canto');
    expect(linhas[0].textContent).toContain('03/09/2026 às 09:15');
    expect(linhas[0].textContent).toContain('Restaurante');
    expect(linhas[0].textContent).toContain('3 km');
    expect(linhas[0].textContent).toContain('8');
    expect(linhas[1].textContent).toContain('Endereço não informado');
    expect(linhas[1].textContent).toContain('Padaria, Farmácia');
    expect(fixture.componentInstance['buscas']()).toEqual(HISTORICO);
  });

  it('mantém a tabela dentro da região útil do workspace', async () => {
    const fixture = await renderizar();
    httpTesting.expectOne(API_ROUTES.buscas).flush(HISTORICO);
    await fixture.whenStable();

    const workspace = fixture.nativeElement.querySelector('.historico-page__workspace');

    expect(workspace?.querySelector('.historico-page__table-region')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.historico-page__eyebrow')).toBeNull();
  });

  it('orienta o usuário quando ainda não existe histórico', async () => {
    const fixture = await renderizar();
    httpTesting.expectOne(API_ROUTES.buscas).flush([]);
    await fixture.whenStable();

    expect(fixture.componentInstance['estado']()).toBe('empty');
    expect(fixture.nativeElement.textContent).toContain('Nenhuma busca registrada');
    expect(fixture.nativeElement.querySelector('a[routerLink="/busca"]')).not.toBeNull();
  });

  it('mostra erro seguro e permite repetir a consulta', async () => {
    const fixture = await renderizar();
    httpTesting.expectOne(API_ROUTES.buscas).flush(
      {
        timestamp: '2026-09-03T12:00:00Z',
        status: 500,
        codigo: 'ERRO_INTERNO',
        mensagem: 'Não foi possível consultar as buscas.',
        path: API_ROUTES.buscas,
      } satisfies ApiErrorResponse,
      { status: 500, statusText: 'Internal Server Error' },
    );
    await fixture.whenStable();

    expect(fixture.componentInstance['estado']()).toBe('error');
    expect(fixture.nativeElement.textContent).toContain('Não foi possível consultar as buscas.');

    const repetir = [...fixture.nativeElement.querySelectorAll('button')].find(
      (button: HTMLButtonElement) => button.textContent?.trim() === 'Tentar novamente',
    ) as HTMLButtonElement;
    repetir.click();

    const novaConsulta = httpTesting.expectOne(API_ROUTES.buscas);
    expect(fixture.componentInstance['estado']()).toBe('loading');
    novaConsulta.flush(HISTORICO);
    await fixture.whenStable();

    expect(fixture.componentInstance['estado']()).toBe('success');
  });

  it('navega pelo link acessível usando o identificador da busca', async () => {
    const fixture = await renderizar();
    httpTesting.expectOne(API_ROUTES.buscas).flush(HISTORICO);
    await fixture.whenStable();

    const link = fixture.nativeElement.querySelector(
      'a[aria-label="Abrir busca 43"]',
    ) as HTMLAnchorElement;
    expect(link).not.toBeNull();
    link.click();
    await fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/historico/43');
  });

  it('trata endereço vazio sem quebrar a apresentação', async () => {
    const fixture = await renderizar();
    httpTesting.expectOne(API_ROUTES.buscas).flush([{ ...HISTORICO[0], enderecoBase: '   ' }]);
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Endereço não informado');
  });
});
