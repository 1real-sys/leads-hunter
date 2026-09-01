import { describe, expect, it } from 'vitest';
import {
  criarBuscaFormInicial,
  criarBuscaRequest,
  listarCategoriasSelecionadas,
  OPCOES_CATEGORIA,
} from './busca-form.model';

describe('modelo do formulário de busca', () => {
  it('inicia com ponto e raio claros, endereço vazio e categorias não selecionadas', () => {
    const modelo = criarBuscaFormInicial();

    expect(modelo).toEqual({
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
    });
  });

  it('mantém rótulos amigáveis associados aos valores exatos do enum', () => {
    expect(OPCOES_CATEGORIA).toEqual([
      { valor: 'MERCADO', rotulo: 'Mercado' },
      { valor: 'PADARIA', rotulo: 'Padaria' },
      { valor: 'DOCERIA', rotulo: 'Doceria' },
      { valor: 'RESTAURANTE', rotulo: 'Restaurante' },
      { valor: 'DISTRIBUIDORA', rotulo: 'Distribuidora' },
      { valor: 'ACOUGUE', rotulo: 'Açougue' },
      { valor: 'FARMACIA', rotulo: 'Farmácia' },
      { valor: 'OUTROS', rotulo: 'Outros' },
    ]);
  });

  it('gera o request com categorias selecionadas sem alterar os enums', () => {
    const modelo = criarBuscaFormInicial();
    modelo.enderecoBase = 'Centro de Vitória';
    modelo.categorias.PADARIA = true;
    modelo.categorias.ACOUGUE = true;

    expect(listarCategoriasSelecionadas(modelo.categorias)).toEqual(['PADARIA', 'ACOUGUE']);
    expect(criarBuscaRequest(modelo)).toEqual({
      enderecoBase: 'Centro de Vitória',
      latitude: -25.4284,
      longitude: -49.2733,
      raioKm: 5,
      categorias: ['PADARIA', 'ACOUGUE'],
    });
  });
});
