import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { BuscaRequest, BuscaResponse } from '../../shared/models/busca.model';
import { API_ROUTES } from './api-routes';

@Service()
export class BuscaApi {
  private readonly http = inject(HttpClient);

  criar(request: BuscaRequest): Observable<BuscaResponse> {
    return this.http.post<BuscaResponse>(API_ROUTES.buscas, request);
  }
}
