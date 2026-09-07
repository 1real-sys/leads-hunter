export type FaixaIdhm =
  | 'MUITO_ALTO'
  | 'ALTO'
  | 'MEDIO'
  | 'BAIXO'
  | 'MUITO_BAIXO'
  | 'SEM_DADO';

export interface ClassificacaoIdhm {
  readonly faixa: FaixaIdhm;
  readonly rotulo: string;
  readonly cor: string;
}

const SEM_DADO: ClassificacaoIdhm = {
  faixa: 'SEM_DADO',
  rotulo: 'Sem IDHM',
  cor: '#94a3b8',
};

const FAIXAS: readonly (ClassificacaoIdhm & { readonly minimo: number })[] = [
  { faixa: 'MUITO_ALTO', rotulo: 'Muito alto', cor: '#1a9850', minimo: 0.8 },
  { faixa: 'ALTO', rotulo: 'Alto', cor: '#91cf60', minimo: 0.7 },
  { faixa: 'MEDIO', rotulo: 'Médio', cor: '#fee08b', minimo: 0.6 },
  { faixa: 'BAIXO', rotulo: 'Baixo', cor: '#fc8d59', minimo: 0.5 },
  { faixa: 'MUITO_BAIXO', rotulo: 'Muito baixo', cor: '#d73027', minimo: 0 },
];

const FORMATADOR_IDHM = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 3,
  maximumFractionDigits: 3,
});

export function classificarIdhm(valor: number | null | undefined): ClassificacaoIdhm {
  if (valor === null || valor === undefined || !Number.isFinite(valor) || valor < 0 || valor > 1) {
    return SEM_DADO;
  }

  const classificacao = FAIXAS.find(({ minimo }) => valor >= minimo);
  if (!classificacao) {
    return SEM_DADO;
  }

  const { faixa, rotulo, cor } = classificacao;
  return { faixa, rotulo, cor };
}

export function formatarIdhm(valor: number | null | undefined): string | null {
  return classificarIdhm(valor).faixa === 'SEM_DADO' || valor === null || valor === undefined
    ? null
    : FORMATADOR_IDHM.format(valor);
}
