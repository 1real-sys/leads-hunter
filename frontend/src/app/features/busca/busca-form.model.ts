import { BuscaRequest } from '../../shared/models/busca.model';
import { CATEGORIAS_NEGOCIO, CategoriaNegocio } from '../../shared/models/enums.model';

export type SelecaoCategorias = Record<CategoriaNegocio, boolean>;

export interface BuscaFormModel {
  enderecoBase: string;
  latitude: number;
  longitude: number;
  raioKm: number;
  categorias: SelecaoCategorias;
}

const ROTULOS_CATEGORIA: Readonly<Record<CategoriaNegocio, string>> = {
  MERCADO: 'Mercado',
  PADARIA: 'Padaria',
  DOCERIA: 'Doceria',
  RESTAURANTE: 'Restaurante',
  DISTRIBUIDORA: 'Distribuidora',
  ACOUGUE: 'Açougue',
  FARMACIA: 'Farmácia',
  OUTROS: 'Outros',
};

export const OPCOES_CATEGORIA = CATEGORIAS_NEGOCIO.map((valor) => ({
  valor,
  rotulo: ROTULOS_CATEGORIA[valor],
}));

export function criarBuscaFormInicial(): BuscaFormModel {
  return {
    enderecoBase: '',
    latitude: -25.4284,
    longitude: -49.2733,
    raioKm: 5,
    categorias: {
      MERCADO: false,
      PADARIA: false,
      DOCERIA: false,
      RESTAURANTE: false,
      DISTRIBUIDORA: false,
      ACOUGUE: false,
      FARMACIA: false,
      OUTROS: false,
    },
  };
}

export function listarCategoriasSelecionadas(selecao: SelecaoCategorias): CategoriaNegocio[] {
  return CATEGORIAS_NEGOCIO.filter((categoria) => selecao[categoria]);
}

export function criarBuscaRequest(modelo: BuscaFormModel): BuscaRequest {
  return {
    enderecoBase: modelo.enderecoBase,
    latitude: modelo.latitude,
    longitude: modelo.longitude,
    raioKm: modelo.raioKm,
    categorias: listarCategoriasSelecionadas(modelo.categorias),
  };
}
