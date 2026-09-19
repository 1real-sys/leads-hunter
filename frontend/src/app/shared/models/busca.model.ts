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
  totalBloqueados?: number;
  criadoEm: LocalDateTimeString;
  leads: LeadEncontradoResponse[];
}

export interface LeadEncontradoResponse {
  id: number;
  nome: string | null;
  categoria: CategoriaNegocio | null;
  enderecoFormatado: string | null;
  website?: string | null;
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
  website?: string | null;
  cnpj?: string | null;
  razaoSocial?: string | null;
  telefone: string | null;
  whatsappUrl: string | null;
  scoreNaBusca: number | null;
  temperaturaNaBusca: Temperatura | null;
  status: StatusFunil | null;
  observacoes: string | null;
  ultimoContatoEm: LocalDateTimeString | null;
}

export interface BuscaCnpjResponse {
  totalLeads: number;
  ignoradosJaComCnpj: number;
  encontrados: number;
  semCorrespondencia: number;
}

export type PesquisaInformacoesStatus =
  'PENDENTE' | 'EM_ANDAMENTO' | 'CONCLUIDA' | 'CONCLUIDA_COM_FALHAS' | 'FALHA';

export interface PesquisaInformacoesExecucaoResponse {
  id: number;
  buscaId: number;
  status: PesquisaInformacoesStatus;
  criadoEm: LocalDateTimeString;
  iniciadoEm: LocalDateTimeString | null;
  atualizadoEm: LocalDateTimeString;
  terminadoEm: LocalDateTimeString | null;
  totalLeads: number;
  progresso: number;
  processados: number;
  ignoradosJaCompletos: number;
  comInstagram: number;
  comSite: number;
  comAmbos: number;
  semInformacoes: number;
  falhas: number;
  erroCodigo: string | null;
  erroMensagem: string | null;
}
