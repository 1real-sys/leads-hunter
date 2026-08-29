import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { getApiErrorMessage, isApiErrorResponse } from './api-error-message';

describe('api-error-message', () => {
  it('retorna a mensagem segura do envelope conhecido da API', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: {
        timestamp: '2026-08-29T12:00:00Z',
        status: 400,
        codigo: 'VALIDACAO_INVALIDA',
        mensagem: 'Confira os dados informados',
        path: '/api/buscas'
      }
    });

    expect(getApiErrorMessage(error)).toBe('Confira os dados informados');
  });

  it('usa fallback legível para status HTTP sem mensagem utilizável', () => {
    expect(getApiErrorMessage(new HttpErrorResponse({ status: 0 })))
      .toContain('conectar ao servidor');
    expect(getApiErrorMessage(new HttpErrorResponse({ status: 429 })))
      .toContain('Limite de requisições');
    expect(getApiErrorMessage(new HttpErrorResponse({ status: 500 })))
      .toContain('erro interno');
  });

  it('não expõe mensagens ou stack traces de erros desconhecidos', () => {
    const privateDetails = 'stack trace interno';

    expect(getApiErrorMessage(new Error(privateDetails))).not.toContain(privateDetails);
    expect(getApiErrorMessage(new HttpErrorResponse({ status: 502, error: privateDetails })))
      .not.toContain(privateDetails);
  });

  it('reconhece somente o formato completo do erro da API', () => {
    expect(isApiErrorResponse({
      timestamp: '2026-08-29T12:00:00Z',
      status: 404,
      codigo: 'LEAD_NAO_ENCONTRADO',
      mensagem: 'Lead não encontrado',
      path: '/api/leads/1'
    })).toBe(true);
    expect(isApiErrorResponse({ status: 404, mensagem: 'incompleto' })).toBe(false);
  });
});
