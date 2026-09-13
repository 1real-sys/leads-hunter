export interface TrechoObservacao {
  texto: string;
  url: string | null;
}

/** Mantém o texto original; somente URLs rotuladas em blocos completos viram links. */
export function separarObservacoesPesquisa(observacoes: string | null): TrechoObservacao[] {
  if (!observacoes) return [{ texto: 'Nenhuma observação registrada.', url: null }];
  const trechos: TrechoObservacao[] = [];
  const blocos =
    /--- Pesquisa inteligente ---\r?\n[\s\S]*?\r?\n--- Fim da pesquisa inteligente ---/g;
  let posicao = 0;
  for (const bloco of observacoes.matchAll(blocos)) {
    trechos.push({ texto: observacoes.slice(posicao, bloco.index), url: null });
    let inicio = 0;
    const links = /^(?:Instagram:|Site próprio:)[ \t]*\r?\n(https?:\/\/[^\r\n]+)/gm;
    for (const link of bloco[0].matchAll(links)) {
      const texto = link[1];
      const indice = link.index + link[0].length - texto.length;
      trechos.push({ texto: bloco[0].slice(inicio, indice), url: null });
      trechos.push({ texto, url: urlSegura(texto) });
      inicio = indice + texto.length;
    }
    trechos.push({ texto: bloco[0].slice(inicio), url: null });
    posicao = bloco.index + bloco[0].length;
  }
  trechos.push({ texto: observacoes.slice(posicao), url: null });
  return trechos.filter((trecho) => trecho.texto.length > 0);
}

function urlSegura(texto: string): string | null {
  if (/[\s<>\\]/.test(texto)) return null;
  try {
    const url = new URL(texto);
    return ['http:', 'https:'].includes(url.protocol) &&
      url.hostname.includes('.') &&
      !url.username &&
      !url.password
      ? url.href
      : null;
  } catch {
    return null;
  }
}
