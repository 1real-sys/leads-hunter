export const API_PREFIX = '/api';

export const API_ROUTES = {
  buscas: `${API_PREFIX}/buscas`,
  leads: `${API_PREFIX}/leads`,
  exportacaoLeadsCsv: `${API_PREFIX}/exportacao/leads.csv`,
  exportacaoLeadsXlsx: `${API_PREFIX}/exportacao/leads.xlsx`
} as const;
