import { InstantString } from './date.model';

export interface ApiErrorResponse {
  timestamp: InstantString;
  status: number;
  codigo: string;
  mensagem: string;
  path: string;
}
