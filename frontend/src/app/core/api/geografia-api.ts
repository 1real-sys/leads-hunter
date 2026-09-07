import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { BboxGeografico, MunicipiosGeoJsonResponse } from '../../shared/models/geografia.model';
import { API_ROUTES } from './api-routes';

@Service()
export class GeografiaApi {
  private readonly http = inject(HttpClient);

  listarMunicipios(bbox: BboxGeografico): Observable<MunicipiosGeoJsonResponse> {
    const valorBbox = [bbox.minLng, bbox.minLat, bbox.maxLng, bbox.maxLat].join(',');
    const params = new HttpParams().set('bbox', valorBbox);

    return this.http.get<MunicipiosGeoJsonResponse>(API_ROUTES.geografiaMunicipios, { params });
  }
}
