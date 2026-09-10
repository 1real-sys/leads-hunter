import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import {
  BuscaCnpjResponse,
  BuscaDetalheResponse,
  BuscaRequest,
  BuscaResponse,
  BuscaResumoResponse,
} from '../../shared/models/busca.model';
import { API_ROUTES } from './api-routes';

@Service()
export class BuscaApi {
  private readonly http = inject(HttpClient);

  criar(request: BuscaRequest): Observable<BuscaResponse> {
    return this.http.post<BuscaResponse>(API_ROUTES.buscas, request);
  }

  listarHistorico(): Observable<BuscaResumoResponse[]> {
    return this.http.get<BuscaResumoResponse[]>(API_ROUTES.buscas);
  }

  buscarHistoricoPorId(id: number): Observable<BuscaDetalheResponse> {
    return this.http.get<BuscaDetalheResponse>(API_ROUTES.busca(id));
  }

  buscarCnpj(id: number): Observable<BuscaCnpjResponse> {
    return this.http.post<BuscaCnpjResponse>(`${API_ROUTES.busca(id)}/cnpj`, null);
  }
}
