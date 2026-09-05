import { LocalDateTimeString } from './date.model';
import { CategoriaNegocio, StatusFunil, Temperatura } from './enums.model';

export interface LeadResponse {
  id: number;
  googlePlaceId: string;
  nome: string | null;
  categoria: CategoriaNegocio | null;
  enderecoFormatado: string | null;
  telefone: string | null;
  telefoneNormalizado: string | null;
  whatsappUrl: string | null;
  latitude: number | null;
  longitude: number | null;
  ratingGoogle: number | null;
  totalReviews: number | null;
  score: number | null;
  temperatura: Temperatura | null;
  status: StatusFunil | null;
  observacoes: string | null;
  ultimoContatoEm: LocalDateTimeString | null;
  criadoEm: LocalDateTimeString | null;
  atualizadoEm: LocalDateTimeString | null;
}

export interface PaginaLeadsResponse {
  leads: readonly LeadResponse[];
  pagina: number;
  tamanho: number;
  totalElementos: number;
  totalPaginas: number;
}

interface CamposAtualizaveisLead {
  status?: StatusFunil | null;
  observacoes?: string | null;
  ultimoContatoEm?: LocalDateTimeString | null;
}

export type AtualizarLeadRequest =
  | (CamposAtualizaveisLead & { status: StatusFunil })
  | (CamposAtualizaveisLead & { observacoes: string })
  | (CamposAtualizaveisLead & { ultimoContatoEm: LocalDateTimeString });
