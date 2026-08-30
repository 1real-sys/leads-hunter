import { describe, expect, it } from 'vitest';
import { pontoMapaValido, raioKmParaMetros } from './mapa.model';

describe('lógica do mapa de busca', () => {
  it('aceita coordenadas geográficas válidas, incluindo os limites', () => {
    expect(pontoMapaValido({ latitude: -90, longitude: -180 })).toBe(true);
    expect(pontoMapaValido({ latitude: 90, longitude: 180 })).toBe(true);
    expect(pontoMapaValido({ latitude: -25.4284, longitude: -49.2733 })).toBe(true);
  });

  it('rejeita coordenadas não finitas ou fora dos limites geográficos', () => {
    expect(pontoMapaValido({ latitude: Number.NaN, longitude: -49 })).toBe(false);
    expect(pontoMapaValido({ latitude: 91, longitude: -49 })).toBe(false);
    expect(pontoMapaValido({ latitude: -25, longitude: -181 })).toBe(false);
  });

  it('converte quilômetros para o raio em metros usado pelo Leaflet', () => {
    expect(raioKmParaMetros(1)).toBe(1_000);
    expect(raioKmParaMetros(5.5)).toBe(5_500);
  });

  it('recusa raios não finitos, iguais a zero ou negativos', () => {
    expect(() => raioKmParaMetros(Number.NaN)).toThrow(RangeError);
    expect(() => raioKmParaMetros(0)).toThrow(RangeError);
    expect(() => raioKmParaMetros(-1)).toThrow(RangeError);
  });
});
