import { describe, expect, it } from 'vitest';
import { separarObservacoesPesquisa } from './observacoes-pesquisa';

const bloco = (texto: string) =>
  `--- Pesquisa inteligente ---\n${texto}\n--- Fim da pesquisa inteligente ---`;

describe('separarObservacoesPesquisa', () => {
  it('preserva cada caractere e torna clicáveis somente os links do bloco', () => {
    const texto =
      'Anotação https://manual.example/\n\n' +
      bloco(
        'Instagram:\nhttps://www.instagram.com/padaria\n\nSite próprio:\nhttps://padaria.example/',
      ) +
      '\nRetornar amanhã.';
    const trechos = separarObservacoesPesquisa(texto);
    expect(trechos.map((trecho) => trecho.texto).join('')).toBe(texto);
    expect(trechos.filter((trecho) => trecho.url).map((trecho) => trecho.url)).toEqual([
      'https://www.instagram.com/padaria',
      'https://padaria.example/',
    ]);
  });

  it.each([
    'javascript:alert(1)',
    'data:text/html,<script>alert(1)</script>',
    'https://usuario:senha@example.com',
    'https://',
    'https://site.example/<script>',
    'https://site.example/ texto',
    '//site.example/',
  ])('não transforma %s em link', (url) => {
    const texto = bloco(`Site próprio:\n${url}`);
    const trechos = separarObservacoesPesquisa(texto);
    expect(trechos.map((trecho) => trecho.texto).join('')).toBe(texto);
    expect(trechos.every((trecho) => trecho.url === null)).toBe(true);
  });

  it('aceita HTTP e CRLF sem alterar texto manual ou ausência', () => {
    const texto = bloco('Site próprio:\nhttp://site.example/').replaceAll('\n', '\r\n');
    expect(separarObservacoesPesquisa(texto).find((trecho) => trecho.url)?.url).toBe(
      'http://site.example/',
    );
    expect(
      separarObservacoesPesquisa(texto)
        .map((trecho) => trecho.texto)
        .join(''),
    ).toBe(texto);
    expect(
      separarObservacoesPesquisa(
        bloco('pesquisa inteligente não encontrou mais informações'),
      ).every((trecho) => !trecho.url),
    ).toBe(true);
  });

  it('não linka texto solto, rótulo desconhecido ou bloco incompleto', () => {
    for (const texto of [
      'Site próprio:\nhttps://fora.example/',
      bloco('Outro:\nhttps://fora.example/'),
      '--- Pesquisa inteligente ---\nSite próprio:\nhttps://incompleto.example/',
    ]) {
      expect(separarObservacoesPesquisa(texto).every((trecho) => !trecho.url)).toBe(true);
    }
    expect(separarObservacoesPesquisa(null)[0].texto).toBe('Nenhuma observação registrada.');
  });
});
