import {
  CategoriaNegocio,
  STATUS_FUNIL,
  StatusFunil,
  Temperatura,
} from '../../shared/models/enums.model';
import { LeadResponse } from '../../shared/models/lead.model';

export const ROTULOS_STATUS: Readonly<Record<StatusFunil, string>> = {
  NOVO: 'Novo',
  QUALIFICADO: 'Qualificado',
  CONTATADO: 'Contatado',
  GANHO: 'Ganho',
  PERDIDO: 'Perdido',
};

export const ROTULOS_CATEGORIA: Readonly<Record<CategoriaNegocio, string>> = {
  MERCADO: 'Mercado',
  PADARIA: 'Padaria',
  DOCERIA: 'Doceria',
  RESTAURANTE: 'Restaurante',
  DISTRIBUIDORA: 'Distribuidora',
  ACOUGUE: 'Açougue',
  FARMACIA: 'Farmácia',
  OUTROS: 'Outros',
};

export const ROTULOS_TEMPERATURA: Readonly<Record<Temperatura, string>> = {
  QUENTE: 'Quente',
  MORNO: 'Morno',
  FRIO: 'Frio',
};

export const TAMANHO_PAGINA_KANBAN = 25;

export type EstadoColunaKanban = 'loading' | 'success' | 'empty' | 'error';

export interface ColunaKanban {
  status: StatusFunil;
  rotulo: string;
  leads: readonly LeadResponse[];
  pagina: number;
  totalPaginas: number;
  totalLeads: number;
  estado: EstadoColunaKanban;
  mensagemErro: string | null;
}

export interface MudancaStatusLead {
  lead: LeadResponse;
  status: StatusFunil;
}

export interface EtapasAdjacentes {
  anterior: StatusFunil | null;
  proxima: StatusFunil | null;
}

export interface PaginaColunaSolicitada {
  status: StatusFunil;
  pagina: number;
}

export function obterRotuloStatus(status: StatusFunil | null): string {
  return status !== null && STATUS_FUNIL.some((statusConhecido) => statusConhecido === status)
    ? ROTULOS_STATUS[status]
    : 'Sem etapa';
}

export function obterEtapasAdjacentes(status: StatusFunil | null): EtapasAdjacentes {
  const indice = status === null ? -1 : STATUS_FUNIL.indexOf(status);

  if (indice < 0) {
    return { anterior: null, proxima: 'NOVO' };
  }

  return {
    anterior: indice > 0 ? STATUS_FUNIL[indice - 1] : null,
    proxima: indice < STATUS_FUNIL.length - 1 ? STATUS_FUNIL[indice + 1] : null,
  };
}

export function criarColunasKanban(): readonly ColunaKanban[] {
  return STATUS_FUNIL.map((status) => ({
    status,
    rotulo: ROTULOS_STATUS[status],
    leads: [],
    pagina: 0,
    totalPaginas: 0,
    totalLeads: 0,
    estado: 'empty',
    mensagemErro: null,
  }));
}
