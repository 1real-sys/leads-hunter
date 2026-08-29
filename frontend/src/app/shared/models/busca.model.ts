import { LocalDateTimeString } from './date.model';
import { CategoriaNegocio, StatusFunil, Temperatura } from './enums.model';

export interface BuscaRequest {
  enderecoBase?: string | null;
  latitude: number;
  longitude: number;
  raioKm: number;
  categorias: CategoriaNegocio[];
}

export interface BuscaResponse {
  id: number;
  enderecoBase: string | null;
  latitude: number;
  longitude: number;
  raioKm: number;
  categorias: CategoriaNegocio[];
  totalEncontrados: number;
  criadoEm: LocalDateTimeString;
  leads: LeadEncontradoResponse[];
}

export interface LeadEncontradoResponse {
  id: number;
  nome: string | null;
  categoria: CategoriaNegocio | null;
  enderecoFormatado: string | null;
  telefone: string | null;
  whatsappUrl: string | null;
  score: number | null;
  temperatura: Temperatura | null;
}

export interface BuscaResumoResponse {
  id: number;
  enderecoBase: string | null;
  latitude: number;
  longitude: number;
  raioKm: number;
  categorias: CategoriaNegocio[];
  totalEncontrados: number;
  criadoEm: LocalDateTimeString;
}

export interface BuscaDetalheResponse {
  id: number;
  enderecoBase: string | null;
  latitude: number;
  longitude: number;
  raioKm: number;
  categorias: CategoriaNegocio[];
  totalEncontrados: number;
  criadoEm: LocalDateTimeString;
  leads: LeadHistoricoResponse[];
}

export interface LeadHistoricoResponse {
  id: number;
  nome: string | null;
  categoria: CategoriaNegocio | null;
  enderecoFormatado: string | null;
  telefone: string | null;
  whatsappUrl: string | null;
  scoreNaBusca: number | null;
  temperaturaNaBusca: Temperatura | null;
  status: StatusFunil | null;
  observacoes: string | null;
  ultimoContatoEm: LocalDateTimeString | null;
}
