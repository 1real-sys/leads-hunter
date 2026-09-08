import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { NomeBloqueadoRequest, NomeBloqueadoResponse } from '../../shared/models/bloqueio.model';
import { API_ROUTES } from './api-routes';

@Service()
export class BloqueioApi {
  private readonly http = inject(HttpClient);

  listar(): Observable<NomeBloqueadoResponse[]> {
    return this.http.get<NomeBloqueadoResponse[]>(API_ROUTES.bloqueios);
  }

  cadastrar(request: NomeBloqueadoRequest): Observable<NomeBloqueadoResponse> {
    return this.http.post<NomeBloqueadoResponse>(API_ROUTES.bloqueios, request);
  }

  remover(id: number): Observable<void> {
    return this.http.delete<void>(API_ROUTES.bloqueio(id));
  }
}
