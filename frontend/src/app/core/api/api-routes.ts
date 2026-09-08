export const API_PREFIX = '/api';

export const API_ROUTES = {
  bloqueios: `${API_PREFIX}/bloqueios`,
  bloqueio: (id: number) => `${API_PREFIX}/bloqueios/${id}`,
  buscas: `${API_PREFIX}/buscas`,
  busca: (id: number) => `${API_PREFIX}/buscas/${id}`,
  leads: `${API_PREFIX}/leads`,
  leadsPagina: `${API_PREFIX}/leads/pagina`,
  lead: (id: number) => `${API_PREFIX}/leads/${id}`,
  geografiaMunicipios: `${API_PREFIX}/geografia/municipios`,
  exportacaoLeadsCsv: `${API_PREFIX}/exportacao/leads.csv`,
  exportacaoLeadsXlsx: `${API_PREFIX}/exportacao/leads.xlsx`,
} as const;
