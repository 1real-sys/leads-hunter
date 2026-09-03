import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { LeadResponse } from '../../shared/models/lead.model';
import { API_ROUTES } from './api-routes';
import { LeadApi } from './lead-api';

const LEADS: LeadResponse[] = [
  {
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
  },
];

describe('LeadApi', () => {
  let api: LeadApi;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(LeadApi);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('lista todos os leads sem enviar filtros vazios', () => {
    let received: LeadResponse[] | undefined;
    api.listar().subscribe((response) => (received = response));

    const request = httpTesting.expectOne(API_ROUTES.leads);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);

    request.flush(LEADS);

    expect(received).toEqual(LEADS);
  });

  it('envia somente os filtros definidos e preserva a ordem da resposta', () => {
    let received: LeadResponse[] | undefined;
    api
      .listar({ status: 'QUALIFICADO', categoria: 'PADARIA', temperatura: 'QUENTE' })
      .subscribe((response) => (received = response));

    const request = httpTesting.expectOne(
      `${API_ROUTES.leads}?status=QUALIFICADO&categoria=PADARIA&temperatura=QUENTE`,
    );
    expect(request.request.method).toBe('GET');

    request.flush(LEADS);

    expect(received).toEqual(LEADS);
  });

  it('atualiza o lead pela rota real com somente os campos permitidos no payload', () => {
    const atualizado = { ...LEADS[0], status: 'CONTATADO' as const };
    let received: LeadResponse | undefined;

    api.atualizar(7, { status: 'CONTATADO' }).subscribe((response) => (received = response));

    const request = httpTesting.expectOne(API_ROUTES.lead(7));
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ status: 'CONTATADO' });

    request.flush(atualizado);

    expect(received).toEqual(atualizado);
  });
});
