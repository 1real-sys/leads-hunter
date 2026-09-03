import { describe, expect, it } from 'vitest';
import { StatusFunil } from '../../shared/models/enums.model';
import { LeadResponse } from '../../shared/models/lead.model';
import { agruparLeadsPorStatus, obterEtapasAdjacentes } from './kanban.model';

function criarLead(id: number, status: StatusFunil | null): LeadResponse {
  return {
    id,
    googlePlaceId: `place-${id}`,
    nome: `Lead ${id}`,
    categoria: null,
    enderecoFormatado: null,
    telefone: null,
    telefoneNormalizado: null,
    whatsappUrl: null,
    latitude: null,
    longitude: null,
    ratingGoogle: null,
    totalReviews: null,
    score: null,
    temperatura: null,
    status,
    observacoes: null,
    ultimoContatoEm: null,
    criadoEm: null,
    atualizadoEm: null,
  };
}

describe('agruparLeadsPorStatus', () => {
  it('cria as cinco etapas na ordem do funil e coloca cada lead exatamente uma vez', () => {
    const leads = [
      criarLead(1, 'CONTATADO'),
      criarLead(2, 'NOVO'),
      criarLead(3, 'GANHO'),
      criarLead(4, 'PERDIDO'),
      criarLead(5, 'QUALIFICADO'),
    ];

    const agrupamento = agruparLeadsPorStatus(leads);
    const idsAgrupados = agrupamento.colunas.flatMap((coluna) =>
      coluna.leads.map((lead) => lead.id),
    );

    expect(agrupamento.colunas.map((coluna) => coluna.status)).toEqual([
      'NOVO',
      'QUALIFICADO',
      'CONTATADO',
      'GANHO',
      'PERDIDO',
    ]);
    expect(idsAgrupados.sort((a, b) => a - b)).toEqual([1, 2, 3, 4, 5]);
    expect(new Set(idsAgrupados).size).toBe(leads.length);
    expect(agrupamento.semEtapa).toBeNull();
  });

  it('preserva a ordenação recebida dentro de cada etapa', () => {
    const leads = [criarLead(10, 'NOVO'), criarLead(8, 'GANHO'), criarLead(7, 'NOVO')];

    const agrupamento = agruparLeadsPorStatus(leads);
    const novos = agrupamento.colunas.find((coluna) => coluna.status === 'NOVO');

    expect(novos?.leads.map((lead) => lead.id)).toEqual([10, 7]);
  });

  it('mantém leads sem status válido em uma coluna explícita sem classificá-los como Novo', () => {
    const semStatus = criarLead(99, null);
    const statusDesconhecido = criarLead(100, 'ARQUIVADO' as StatusFunil);

    const agrupamento = agruparLeadsPorStatus([semStatus, statusDesconhecido]);

    expect(agrupamento.colunas.find((coluna) => coluna.status === 'NOVO')?.leads).toEqual([]);
    expect(agrupamento.semEtapa).toEqual({
      status: null,
      rotulo: 'Sem etapa',
      leads: [semStatus, statusDesconhecido],
    });
  });
});

describe('obterEtapasAdjacentes', () => {
  it('retorna somente destinos válidos do funil', () => {
    expect(obterEtapasAdjacentes('NOVO')).toEqual({ anterior: null, proxima: 'QUALIFICADO' });
    expect(obterEtapasAdjacentes('CONTATADO')).toEqual({
      anterior: 'QUALIFICADO',
      proxima: 'GANHO',
    });
    expect(obterEtapasAdjacentes('PERDIDO')).toEqual({ anterior: 'GANHO', proxima: null });
  });

  it('oferece Novo como único destino para um status ausente ou desconhecido', () => {
    expect(obterEtapasAdjacentes(null)).toEqual({ anterior: null, proxima: 'NOVO' });
    expect(obterEtapasAdjacentes('ARQUIVADO' as StatusFunil)).toEqual({
      anterior: null,
      proxima: 'NOVO',
    });
  });
});
