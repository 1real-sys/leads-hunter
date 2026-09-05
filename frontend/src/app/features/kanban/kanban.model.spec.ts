import { describe, expect, it } from 'vitest';
import { StatusFunil } from '../../shared/models/enums.model';
import { criarColunasKanban, obterEtapasAdjacentes } from './kanban.model';

describe('criarColunasKanban', () => {
  it('cria as cinco etapas independentes na ordem do funil', () => {
    const colunas = criarColunasKanban();

    expect(colunas.map((coluna) => coluna.status)).toEqual([
      'NOVO',
      'QUALIFICADO',
      'CONTATADO',
      'GANHO',
      'PERDIDO',
    ]);
    expect(colunas.every((coluna) => coluna.leads.length === 0)).toBe(true);
    expect(colunas.every((coluna) => coluna.pagina === 0 && coluna.totalPaginas === 0)).toBe(true);
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
