import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { NomeBloqueadoResponse } from '../../shared/models/bloqueio.model';
import { API_ROUTES } from './api-routes';
import { BloqueioApi } from './bloqueio-api';

const BLOQUEIO: NomeBloqueadoResponse = {
  id: 7,
  termo: 'Supermercados BH',
  criadoEm: '2026-09-08T10:30:00',
};

describe('BloqueioApi', () => {
  let api: BloqueioApi;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(BloqueioApi);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('lista os bloqueios cadastrados com GET', () => {
    let recebido: NomeBloqueadoResponse[] | undefined;
    api.listar().subscribe((response) => (recebido = response));

    const request = httpTesting.expectOne(API_ROUTES.bloqueios);
    expect(request.request.method).toBe('GET');
    expect(request.request.body).toBeNull();
    request.flush([BLOQUEIO]);

    expect(recebido).toEqual([BLOQUEIO]);
  });

  it('cadastra um termo com POST tipado', () => {
    let recebido: NomeBloqueadoResponse | undefined;
    api.cadastrar({ termo: 'Supermercados BH' }).subscribe((response) => (recebido = response));

    const request = httpTesting.expectOne(API_ROUTES.bloqueios);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ termo: 'Supermercados BH' });
    request.flush(BLOQUEIO, { status: 201, statusText: 'Created' });

    expect(recebido).toEqual(BLOQUEIO);
  });

  it('remove um termo por id com DELETE', () => {
    let concluiu = false;
    api.remover(7).subscribe(() => (concluiu = true));

    const request = httpTesting.expectOne(API_ROUTES.bloqueio(7));
    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toBeNull();
    request.flush(null, { status: 204, statusText: 'No Content' });

    expect(concluiu).toBe(true);
  });
});
