import { mkdirSync } from 'node:fs';
import AxeBuilder from '@axe-core/playwright';
import { firefox } from 'playwright';

const BASE_URL = process.env.APP_URL ?? 'http://localhost:4200';
const SAIDA = '/tmp/opencode/leadshunter-visual';
mkdirSync(SAIDA, { recursive: true });

const rotas = [
  { nome: 'kanban', caminho: '/kanban', seletor: '.kanban-board, .kanban-page__empty' },
  { nome: 'busca', caminho: '/busca', seletor: '.leaflet-container' },
];

const navegador = await firefox.launch({ headless: true });
const contexto = await navegador.newContext({ viewport: { width: 1440, height: 1200 } });
const pagina = await contexto.newPage();

const relatorio = [];

for (const rota of rotas) {
  const errosPagina = [];
  let violacoes = null;
  let cards = null;

  try {
    await pagina.goto(`${BASE_URL}${rota.caminho}`, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await pagina.waitForSelector(rota.seletor, { timeout: 20000 });
    await pagina.waitForTimeout(1500);

    const auditoria = await new AxeBuilder({ page: pagina })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    violacoes = auditoria.violations.map((v) => ({
      id: v.id,
      impacto: v.impact,
      ajuda: v.help,
      nos: v.nodes.length,
    }));

    cards = await pagina.locator('.lead-card').count();
    await pagina.screenshot({ path: `${SAIDA}/${rota.nome}.png`, fullPage: false });
  } catch (erro) {
    errosPagina.push(String(erro).slice(0, 500));
    await pagina.screenshot({ path: `${SAIDA}/${rota.nome}-erro.png`, fullPage: false });
  }

  relatorio.push({ rota: rota.nome, cards, violacoes, erros: errosPagina });
}

await navegador.close();

console.log(JSON.stringify(relatorio, null, 2));
const comViolacao = relatorio.filter((r) => (r.violacoes?.length ?? 0) > 0);
process.exit(comViolacao.length > 0 ? 1 : 0);
