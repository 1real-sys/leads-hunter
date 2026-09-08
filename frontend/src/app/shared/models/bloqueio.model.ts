import { LocalDateTimeString } from './date.model';

export interface NomeBloqueadoRequest {
  termo: string;
}

export interface NomeBloqueadoResponse {
  id: number;
  termo: string;
  criadoEm: LocalDateTimeString;
}
