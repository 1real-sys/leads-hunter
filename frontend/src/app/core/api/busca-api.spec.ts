import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ApiErrorResponse } from '../../shared/models/api-error-response.model';
import { BuscaRequest, BuscaResponse } from '../../shared/models/busca.model';
import { API_ROUTES } from './api-routes';
import { BuscaApi } from './busca-api';

const REQUEST: BuscaRequest = {
  enderecoBase: 'Centro de Vitória',
  latitude: -20.3155,
  longitude: -40.3128,
  raioKm: 5,
  categorias: ['PADARIA', 'FARMACIA'],
};

const RESPONSE: BuscaResponse = {
  id: 42,
  enderecoBase: REQUEST.enderecoBase ?? null,
  latitude: REQUEST.latitude,
  longitude: REQUEST.longitude,
  raioKm: REQUEST.raioKm,
  categorias: REQUEST.categorias,
  totalEncontrados: 1,
  criadoEm: '2026-08-31T10:30:00',
  leads: [
    {
      id: 7,
      nome: 'Padaria Central',
      categoria: 'PADARIA',
      enderecoFormatado: 'Rua Central, 100',
      telefone: '(27) 3333-4444',
      whatsappUrl: 'https://wa.me/552733334444',
      score: 80,
      temperatura: 'QUENTE',
    },
  ],
};

describe('BuscaApi', () => {
  let api: BuscaApi;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(BuscaApi);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('envia POST tipado para a rota de buscas e devolve a resposta completa', () => {
    let received: BuscaResponse | undefined;
    api.criar(REQUEST).subscribe((response) => (received = response));

    const request = httpTesting.expectOne(API_ROUTES.buscas);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(REQUEST);

    request.flush(RESPONSE, { status: 201, statusText: 'Created' });

    expect(received).toEqual(RESPONSE);
  });

  it.each([400, 429, 502, 503, 500])(
    'propaga o contrato de erro da API para o status %i',
    (status) => {
      const errorBody: ApiErrorResponse = {
        timestamp: '2026-08-31T13:30:00Z',
        status,
        codigo: `ERRO_${status}`,
        mensagem: `Mensagem segura para ${status}`,
        path: API_ROUTES.buscas,
      };
      let receivedError: unknown;
      api.criar(REQUEST).subscribe({ error: (error: unknown) => (receivedError = error) });

      const request = httpTesting.expectOne(API_ROUTES.buscas);
      request.flush(errorBody, { status, statusText: `Error ${status}` });

      expect(receivedError).toMatchObject({ status, error: errorBody });
    },
  );
});
