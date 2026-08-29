export const CATEGORIAS_NEGOCIO = [
  'MERCADO',
  'PADARIA',
  'DOCERIA',
  'RESTAURANTE',
  'DISTRIBUIDORA',
  'ACOUGUE',
  'FARMACIA',
  'OUTROS'
] as const;

export type CategoriaNegocio = (typeof CATEGORIAS_NEGOCIO)[number];

export const STATUS_FUNIL = [
  'NOVO',
  'QUALIFICADO',
  'CONTATADO',
  'GANHO',
  'PERDIDO'
] as const;

export type StatusFunil = (typeof STATUS_FUNIL)[number];

export const TEMPERATURAS = ['QUENTE', 'MORNO', 'FRIO'] as const;

export type Temperatura = (typeof TEMPERATURAS)[number];
