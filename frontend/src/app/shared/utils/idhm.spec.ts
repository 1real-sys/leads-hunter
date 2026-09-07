import { describe, expect, it } from 'vitest';
import { classificarIdhm, formatarIdhm } from './idhm';

describe('IDHM', () => {
  it.each([
    [0.8, 'MUITO_ALTO', 'Muito alto', '#1a9850'],
    [1, 'MUITO_ALTO', 'Muito alto', '#1a9850'],
    [0.7, 'ALTO', 'Alto', '#91cf60'],
    [0.799, 'ALTO', 'Alto', '#91cf60'],
    [0.6, 'MEDIO', 'Médio', '#fee08b'],
    [0.699, 'MEDIO', 'Médio', '#fee08b'],
    [0.5, 'BAIXO', 'Baixo', '#fc8d59'],
    [0.599, 'BAIXO', 'Baixo', '#fc8d59'],
    [0, 'MUITO_BAIXO', 'Muito baixo', '#d73027'],
    [0.499, 'MUITO_BAIXO', 'Muito baixo', '#d73027'],
  ])('classifica %s na faixa PNUD correta', (valor, faixa, rotulo, cor) => {
    expect(classificarIdhm(valor)).toEqual({ faixa, rotulo, cor });
  });

  it.each([null, undefined, Number.NaN, Number.POSITIVE_INFINITY, -0.001, 1.001])(
    'trata %s como dado ausente ou inválido',
    (valor) => {
      expect(classificarIdhm(valor)).toEqual({
        faixa: 'SEM_DADO',
        rotulo: 'Sem IDHM',
        cor: '#94a3b8',
      });
      expect(formatarIdhm(valor)).toBeNull();
    },
  );

  it('formata o índice com três casas no padrão brasileiro', () => {
    expect(formatarIdhm(0.845)).toBe('0,845');
    expect(formatarIdhm(0.7)).toBe('0,700');
  });
});
