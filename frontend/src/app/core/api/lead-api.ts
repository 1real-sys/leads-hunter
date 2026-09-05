import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { CategoriaNegocio, StatusFunil, Temperatura } from '../../shared/models/enums.model';
import {
  AtualizarLeadRequest,
  LeadResponse,
  PaginaLeadsResponse,
} from '../../shared/models/lead.model';
import { API_ROUTES } from './api-routes';

export interface FiltrosLead {
  status?: StatusFunil;
  categoria?: CategoriaNegocio;
  temperatura?: Temperatura;
}

export interface ConsultaPaginaLeads extends FiltrosLead {
  status: StatusFunil;
  page: number;
  size: number;
}

@Service()
export class LeadApi {
  private readonly http = inject(HttpClient);

  listar(filtros: FiltrosLead = {}): Observable<LeadResponse[]> {
    let params = new HttpParams();

    if (filtros.status !== undefined) {
      params = params.set('status', filtros.status);
    }
    if (filtros.categoria !== undefined) {
      params = params.set('categoria', filtros.categoria);
    }
    if (filtros.temperatura !== undefined) {
      params = params.set('temperatura', filtros.temperatura);
    }

    return this.http.get<LeadResponse[]>(API_ROUTES.leads, { params });
  }

  listarPagina(consulta: ConsultaPaginaLeads): Observable<PaginaLeadsResponse> {
    let params = new HttpParams()
      .set('status', consulta.status)
      .set('page', consulta.page)
      .set('size', consulta.size);

    if (consulta.categoria !== undefined) {
      params = params.set('categoria', consulta.categoria);
    }
    if (consulta.temperatura !== undefined) {
      params = params.set('temperatura', consulta.temperatura);
    }

    return this.http.get<PaginaLeadsResponse>(API_ROUTES.leadsPagina, { params });
  }

  atualizar(id: number, request: AtualizarLeadRequest): Observable<LeadResponse> {
    return this.http.patch<LeadResponse>(API_ROUTES.lead(id), request);
  }
}
