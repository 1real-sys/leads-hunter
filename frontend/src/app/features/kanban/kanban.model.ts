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

export interface ColunaKanban {
  status: StatusFunil | null;
  rotulo: string;
  leads: readonly LeadResponse[];
}

export interface AgrupamentoKanban {
  colunas: readonly ColunaKanban[];
  semEtapa: ColunaKanban | null;
}

export function obterRotuloStatus(status: StatusFunil | null): string {
  return status !== null && STATUS_FUNIL.some((statusConhecido) => statusConhecido === status)
    ? ROTULOS_STATUS[status]
    : 'Sem etapa';
}

function possuiStatusConhecido(lead: LeadResponse): lead is LeadResponse & { status: StatusFunil } {
  return lead.status !== null && STATUS_FUNIL.some((status) => status === lead.status);
}

export function agruparLeadsPorStatus(leads: readonly LeadResponse[]): AgrupamentoKanban {
  const grupos: Record<StatusFunil, LeadResponse[]> = {
    NOVO: [],
    QUALIFICADO: [],
    CONTATADO: [],
    GANHO: [],
    PERDIDO: [],
  };
  const semEtapa: LeadResponse[] = [];

  for (const lead of leads) {
    if (possuiStatusConhecido(lead)) {
      grupos[lead.status].push(lead);
    } else {
      semEtapa.push(lead);
    }
  }

  return {
    colunas: STATUS_FUNIL.map((status) => ({
      status,
      rotulo: ROTULOS_STATUS[status],
      leads: grupos[status],
    })),
    semEtapa:
      semEtapa.length > 0
        ? {
            status: null,
            rotulo: 'Sem etapa',
            leads: semEtapa,
          }
        : null,
  };
}
