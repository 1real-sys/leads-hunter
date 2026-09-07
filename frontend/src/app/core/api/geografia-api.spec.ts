import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { MunicipiosGeoJsonResponse } from '../../shared/models/geografia.model';
import { API_ROUTES } from './api-routes';
import { GeografiaApi } from './geografia-api';

const RESPONSE: MunicipiosGeoJsonResponse = {
  type: 'FeatureCollection',
  features: [
    {
      type: 'Feature',
      properties: {
        codigoIbge: '3205309',
        nome: 'Vitória',
        uf: 'ES',
        idhm: 0.845,
        idhmReferencia: 2010,
      },
      geometry: {
        type: 'Polygon',
        coordinates: [
          [
            [-40.34, -20.32],
            [-40.31, -20.29],
            [-40.34, -20.32],
          ],
        ],
      },
    },
  ],
};

describe('GeografiaApi', () => {
  let api: GeografiaApi;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(GeografiaApi);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('consulta os municípios com o bbox na ordem esperada pelo backend', () => {
    let recebido: MunicipiosGeoJsonResponse | undefined;

    api
      .listarMunicipios({ minLng: -40.4, minLat: -20.4, maxLng: -40.2, maxLat: -20.2 })
      .subscribe((response) => (recebido = response));

    const request = httpTesting.expectOne(
      (candidate) => candidate.url === API_ROUTES.geografiaMunicipios,
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('bbox')).toBe('-40.4,-20.4,-40.2,-20.2');
    expect(request.request.body).toBeNull();

    request.flush(RESPONSE);

    expect(recebido).toEqual(RESPONSE);
  });
});
