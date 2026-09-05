import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, input, output } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_ROUTES } from '../../core/api/api-routes';
import { ApiErrorResponse } from '../../shared/models/api-error-response.model';
import { BuscaRequest, BuscaResponse } from '../../shared/models/busca.model';
import { BuscaPage } from './busca-page';
import { MapaBusca } from './mapa-busca';
import { PontoMapa } from './mapa.model';

@Component({
  selector: 'app-mapa-busca',
  template: '',
})
class MapaBuscaStub {
  readonly pontoCentral = input.required<PontoMapa>();
  readonly raioKm = input.required<number>();
  readonly pontoCentralChange = output<PontoMapa>();
}

const REQUEST: BuscaRequest = {
  enderecoBase: '',
  latitude: -25.4284,
  longitude: -49.2733,
  raioKm: 5,
  categorias: ['MERCADO'],
};

const RESPONSE: BuscaResponse = {
  id: 42,
  enderecoBase: '',
  latitude: REQUEST.latitude,
  longitude: REQUEST.longitude,
  raioKm: REQUEST.raioKm,
  categorias: REQUEST.categorias,
  totalEncontrados: 1,
  criadoEm: '2026-08-31T10:30:00',
  leads: [
    {
      id: 7,
      nome: 'Mercado Central',
      categoria: 'MERCADO',
      enderecoFormatado: 'Rua Central, 100',
      telefone: null,
      whatsappUrl: null,
      score: 55,
      temperatura: 'MORNO',
    },
  ],
};

describe('BuscaPage', () => {
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BuscaPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.overrideComponent(BuscaPage, {
      remove: { imports: [MapaBusca] },
      add: { imports: [MapaBuscaStub] },
    });
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function createFixture() {
    const fixture = TestBed.createComponent(BuscaPage);
    await fixture.whenStable();
    return fixture;
  }

  async function prepararBuscaValida(fixture: Awaited<ReturnType<typeof createFixture>>) {
    const market = fixture.nativeElement.querySelector(
      '[data-categoria="MERCADO"]',
    ) as HTMLInputElement;
    market.click();
    await fixture.whenStable();
  }

  function enviarBusca(fixture: Awaited<ReturnType<typeof createFixture>>): void {
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new SubmitEvent('submit', { bubbles: true, cancelable: true }));
  }

  it('organiza os parâmetros na região operacional e mantém o mapa no workspace principal', async () => {
    const fixture = await createFixture();
    const controls = fixture.nativeElement.querySelector(
      'aside.busca-page__controls',
    ) as HTMLElement;
    const workspace = fixture.nativeElement.querySelector('.busca-page__workspace') as HTMLElement;

    expect(controls.getAttribute('aria-labelledby')).toBe('busca-page-title');
    expect(controls.querySelector('app-busca-form')).toBeTruthy();
    expect(workspace.querySelector('app-mapa-busca')).toBeTruthy();
    expect(workspace.contains(controls)).toBe(false);
  });

  it('atualiza latitude e longitude do formulário quando o mapa emite um novo ponto', async () => {
    const fixture = await createFixture();
    const map = fixture.debugElement.query(By.directive(MapaBuscaStub))
      .componentInstance as MapaBuscaStub;

    map.pontoCentralChange.emit({ latitude: -20.3155, longitude: -40.3128 });
    await fixture.whenStable();

    const latitude = fixture.nativeElement.querySelector('#latitude') as HTMLInputElement;
    const longitude = fixture.nativeElement.querySelector('#longitude') as HTMLInputElement;
    expect(latitude.value).toBe('-20.3155');
    expect(longitude.value).toBe('-40.3128');
    expect(map.pontoCentral()).toEqual({ latitude: -20.3155, longitude: -40.3128 });
  });

  it('atualiza o ponto recebido pelo mapa quando as coordenadas do formulário mudam', async () => {
    const fixture = await createFixture();
    const map = fixture.debugElement.query(By.directive(MapaBuscaStub))
      .componentInstance as MapaBuscaStub;
    const latitude = fixture.nativeElement.querySelector('#latitude') as HTMLInputElement;
    const longitude = fixture.nativeElement.querySelector('#longitude') as HTMLInputElement;

    latitude.value = '-19.923456';
    latitude.dispatchEvent(new Event('input'));
    longitude.value = '-43.934567';
    longitude.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    expect(map.pontoCentral()).toEqual({ latitude: -19.923456, longitude: -43.934567 });
  });

  it('expõe o erro imediatamente quando uma coordenada editada fica inválida', async () => {
    const fixture = await createFixture();
    const latitude = fixture.nativeElement.querySelector('#latitude') as HTMLInputElement;

    latitude.value = '91';
    latitude.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    expect(latitude.getAttribute('aria-invalid')).toBe('true');
    expect(fixture.nativeElement.querySelector('#latitude-error')?.textContent).toContain(
      'latitude máxima é 90',
    );
  });

  it('sincroniza o slider de raio com o valor recebido pelo mapa', async () => {
    const fixture = await createFixture();
    const map = fixture.debugElement.query(By.directive(MapaBuscaStub))
      .componentInstance as MapaBuscaStub;
    const radius = fixture.nativeElement.querySelector('#raio-km') as HTMLInputElement;

    radius.value = '12';
    radius.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    expect(map.raioKm()).toBe(12);
    expect(fixture.nativeElement.querySelector('output')?.textContent).toContain('12 km');
  });

  it('envia uma busca válida, bloqueia duplicidade e mantém a resposta completa', async () => {
    const fixture = await createFixture();
    await prepararBuscaValida(fixture);

    enviarBusca(fixture);
    await fixture.whenStable();
    enviarBusca(fixture);
    await fixture.whenStable();

    const button = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain('Buscando leads');
    expect(fixture.nativeElement.textContent).toContain('Busca em andamento');

    const requests = httpTesting.match(API_ROUTES.buscas);
    expect(requests).toHaveLength(1);
    expect(requests[0].request.method).toBe('POST');
    expect(requests[0].request.body).toEqual(REQUEST);

    requests[0].flush(RESPONSE, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(fixture.componentInstance['estadoOperacao']()).toBe('success');
    expect(fixture.componentInstance['resultadoBusca']()).toEqual(RESPONSE);
    expect(button.disabled).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Busca concluída com sucesso.');
    expect(fixture.nativeElement.textContent).toContain('1 lead encontrado.');
    expect(fixture.nativeElement.textContent).toContain(
      'Verifique o Kanban para trabalhar os leads ou o Histórico para consultar a busca.',
    );
    expect(
      fixture.nativeElement.querySelector('a[routerLink="/kanban"]')?.textContent,
    ).toContain('Ver Kanban');
    expect(
      fixture.nativeElement.querySelector('a[routerLink="/historico"]')?.textContent,
    ).toContain('Ver Histórico');
    expect(fixture.nativeElement.textContent).toContain('Busca #42 concluída');
    expect(fixture.nativeElement.textContent).toContain('1 lead encontrado');
    expect(fixture.nativeElement.textContent).toContain('Mercado Central');
  });

  it.each([
    [400, 'VALIDACAO_INVALIDA'],
    [429, 'GOOGLE_PLACES_RATE_LIMIT'],
    [502, 'GOOGLE_PLACES_INVALID_RESPONSE'],
    [503, 'GOOGLE_PLACES_UNAVAILABLE'],
    [500, 'ERRO_INTERNO'],
  ] as const)(
    'apresenta o erro seguro retornado pelo backend para o status %i',
    async (status, codigo) => {
      const fixture = await createFixture();
      await prepararBuscaValida(fixture);
      enviarBusca(fixture);

      const errorBody: ApiErrorResponse = {
        timestamp: '2026-08-31T13:30:00Z',
        status,
        codigo,
        mensagem: `Mensagem segura para ${status}`,
        path: API_ROUTES.buscas,
      };
      httpTesting
        .expectOne(API_ROUTES.buscas)
        .flush(errorBody, { status, statusText: `Error ${status}` });
      await fixture.whenStable();

      const button = fixture.nativeElement.querySelector(
        'button[type="submit"]',
      ) as HTMLButtonElement;
      expect(fixture.componentInstance['estadoOperacao']()).toBe('error');
      expect(fixture.componentInstance['resultadoBusca']()).toBeNull();
      expect(button.disabled).toBe(false);
      expect(fixture.nativeElement.textContent).toContain(errorBody.mensagem);
      expect(fixture.nativeElement.textContent).toContain('tentar novamente');
      expect(fixture.nativeElement.textContent).not.toContain('Busca concluída com sucesso');
    },
  );

  it('permite tentar novamente com segurança depois de uma falha', async () => {
    const fixture = await createFixture();
    await prepararBuscaValida(fixture);
    enviarBusca(fixture);

    httpTesting.expectOne(API_ROUTES.buscas).flush(
      {
        timestamp: '2026-08-31T13:30:00Z',
        status: 503,
        codigo: 'GOOGLE_PLACES_UNAVAILABLE',
        mensagem: 'O serviço externo está temporariamente indisponível.',
        path: API_ROUTES.buscas,
      } satisfies ApiErrorResponse,
      { status: 503, statusText: 'Service Unavailable' },
    );
    await fixture.whenStable();

    enviarBusca(fixture);
    const retry = httpTesting.expectOne(API_ROUTES.buscas);
    expect(retry.request.body).toEqual(REQUEST);
    retry.flush(RESPONSE, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(fixture.componentInstance['estadoOperacao']()).toBe('success');
    expect(fixture.componentInstance['mensagemErro']()).toBeNull();
    expect(fixture.componentInstance['resultadoBusca']()).toEqual(RESPONSE);
  });

  it('trata uma resposta sem leads como sucesso vazio e preserva seus dados', async () => {
    const fixture = await createFixture();
    await prepararBuscaValida(fixture);
    enviarBusca(fixture);

    const emptyResponse: BuscaResponse = {
      ...RESPONSE,
      totalEncontrados: 0,
      leads: [],
    };
    httpTesting
      .expectOne(API_ROUTES.buscas)
      .flush(emptyResponse, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(fixture.componentInstance['estadoOperacao']()).toBe('empty');
    expect(fixture.componentInstance['resultadoBusca']()).toEqual(emptyResponse);
    expect(fixture.componentInstance['mensagemErro']()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain(
      'Busca concluída. Nenhum lead foi encontrado com os parâmetros informados.',
    );
    expect(fixture.nativeElement.textContent).not.toContain('Busca concluída com sucesso');
    expect(fixture.nativeElement.textContent).toContain('Nenhum lead encontrado');
    expect(fixture.nativeElement.textContent).not.toContain('encontrou um problema');
  });
});
