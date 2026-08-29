import { describe, expect, it } from 'vitest';
import { API_PREFIX, API_ROUTES } from '../../core/api/api-routes';
import { ApiErrorResponse } from './api-error-response.model';
import { LocalDateTimeString } from './date.model';
import {
  CATEGORIAS_NEGOCIO,
  STATUS_FUNIL,
  TEMPERATURAS,
  CategoriaNegocio,
  StatusFunil,
  Temperatura
} from './enums.model';
import { AtualizarLeadRequest, LeadResponse } from './lead.model';
import { BuscaDetalheResponse, BuscaRequest, BuscaResponse, BuscaResumoResponse } from './busca.model';

describe('contratos TypeScript da API', () => {
  it('mantém enums e prefixo alinhados aos contratos reais', () => {
    expect(CATEGORIAS_NEGOCIO).toEqual([
      'MERCADO', 'PADARIA', 'DOCERIA', 'RESTAURANTE',
      'DISTRIBUIDORA', 'ACOUGUE', 'FARMACIA', 'OUTROS'
    ]);
    expect(STATUS_FUNIL).toEqual(['NOVO', 'QUALIFICADO', 'CONTATADO', 'GANHO', 'PERDIDO']);
    expect(TEMPERATURAS).toEqual(['QUENTE', 'MORNO', 'FRIO']);
    expect(API_ROUTES.buscas).toBe('/api/buscas');
    expect(API_ROUTES.leads).toBe('/api/leads');
    expect(API_PREFIX).toBe('/api');
  });

  it('compila os DTOs com datas locais, nulos e campos opcionais', () => {
    const localDateTime: LocalDateTimeString = '2026-08-29T12:00:00';
    const categoria: CategoriaNegocio = 'PADARIA';
    const status: StatusFunil = 'CONTATADO';
    const temperatura: Temperatura = 'QUENTE';

    const request = {
      enderecoBase: null,
      latitude: -25.4284,
      longitude: -49.2733,
      raioKm: 5,
      categorias: [categoria]
    } satisfies BuscaRequest;

    const busca = {
      id: 10,
      enderecoBase: null,
      latitude: request.latitude,
      longitude: request.longitude,
      raioKm: request.raioKm,
      categorias: request.categorias,
      totalEncontrados: 0,
      criadoEm: localDateTime,
      leads: []
    } satisfies BuscaResponse;

    const resumo = {
      id: busca.id,
      enderecoBase: busca.enderecoBase,
      latitude: busca.latitude,
      longitude: busca.longitude,
      raioKm: busca.raioKm,
      categorias: busca.categorias,
      totalEncontrados: busca.totalEncontrados,
      criadoEm: busca.criadoEm
    } satisfies BuscaResumoResponse;

    const detalhe = {
      ...resumo,
      leads: []
    } satisfies BuscaDetalheResponse;

    const lead = {
      id: 20,
      googlePlaceId: 'place-20',
      nome: 'Padaria Central',
      categoria,
      enderecoFormatado: null,
      telefone: null,
      telefoneNormalizado: null,
      whatsappUrl: null,
      latitude: null,
      longitude: null,
      ratingGoogle: null,
      totalReviews: null,
      score: null,
      temperatura,
      status,
      observacoes: null,
      ultimoContatoEm: null,
      criadoEm: localDateTime,
      atualizadoEm: localDateTime
    } satisfies LeadResponse;

    const update = {
      status,
      observacoes: null,
      ultimoContatoEm: localDateTime
    } satisfies AtualizarLeadRequest;

    const apiError = {
      timestamp: '2026-08-29T15:00:00Z',
      status: 400,
      codigo: 'VALIDACAO_INVALIDA',
      mensagem: 'Dados inválidos',
      path: '/api/leads/20'
    } satisfies ApiErrorResponse;

    expect({ busca, resumo, detalhe, lead, update, apiError, temperatura }).toBeDefined();
  });
});
