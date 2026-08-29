import { HttpErrorResponse } from '@angular/common/http';
import { ApiErrorResponse } from '../../shared/models/api-error-response.model';

const FALLBACK_MESSAGE = 'Não foi possível concluir a operação. Tente novamente.';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

export function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  if (!isRecord(value)) {
    return false;
  }

  return typeof value['timestamp'] === 'string'
    && typeof value['status'] === 'number'
    && typeof value['codigo'] === 'string'
    && typeof value['mensagem'] === 'string'
    && typeof value['path'] === 'string';
}

function messageForStatus(status: number): string {
  switch (status) {
    case 0:
      return 'Não foi possível conectar ao servidor. Verifique se o backend está em execução.';
    case 400:
      return 'Não foi possível processar a requisição. Confira os dados informados.';
    case 404:
      return 'O recurso solicitado não foi encontrado.';
    case 429:
      return 'Limite de requisições atingido. Tente novamente em instantes.';
    case 502:
      return 'O serviço externo está temporariamente indisponível. Tente novamente.';
    case 503:
      return 'O serviço está temporariamente indisponível. Tente novamente mais tarde.';
    case 500:
      return 'Ocorreu um erro interno. Tente novamente mais tarde.';
    default:
      return FALLBACK_MESSAGE;
  }
}

export function getApiErrorMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return FALLBACK_MESSAGE;
  }

  if (isApiErrorResponse(error.error) && error.error.mensagem.trim().length > 0) {
    return error.error.mensagem;
  }

  return messageForStatus(error.status);
}
