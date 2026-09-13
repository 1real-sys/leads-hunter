import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import AxeBuilder from '@axe-core/playwright';
import { firefox, type Page } from 'playwright';

const baseUrl = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:4300';
const saida = mkdtempSync(join(tmpdir(), 'leads-hunter-info016-'));
const bloco = (texto: string) =>
  `--- Pesquisa inteligente ---\n${texto}\n--- Fim da pesquisa inteligente ---`;
const observacoes = [
  'Anotação manual <script>não executar</script>\n\n' +
    bloco(
      'Instagram:\nhttps://www.instagram.com/padaria\n\nSite próprio:\nhttps://padaria.example/',
    ),
  bloco('Instagram:\nhttps://www.instagram.com/farmacia'),
  bloco('Site próprio:\nhttps://site.example/'),
  bloco('pesquisa inteligente não encontrou mais informações'),
];
const resumo = {
  id: 42,
  enderecoBase: 'Centro de Vitória',
  latitude: -20.3155,
  longitude: -40.3128,
  raioKm: 5,
  categorias: ['PADARIA'],
  totalEncontrados: 4,
  criadoEm: '2026-09-13T01:00:00',
};
const leads = observacoes.map((obs, i) => ({
  id: i + 1,
  nome: `Estabelecimento de teste ${i + 1}`,
  categoria: 'PADARIA',
  enderecoFormatado: 'Rua de teste, 10',
  cnpj: null,
  razaoSocial: null,
  telefone: null,
  whatsappUrl: null,
  scoreNaBusca: 80,
  temperaturaNaBusca: 'QUENTE',
  status: 'NOVO',
  observacoes: obs,
  ultimoContatoEm: null,
}));
const execucaoBase = {
  id: 90,
  buscaId: 42,
  status: 'PENDENTE',
  criadoEm: '2026-09-13T01:00:00',
  iniciadoEm: '2026-09-13T01:00:00',
  atualizadoEm: '2026-09-13T01:00:00',
  terminadoEm: null as string | null,
  totalLeads: 4,
  progresso: 0,
  processados: 0,
  ignoradosJaCompletos: 0,
  comInstagram: 0,
  comSite: 0,
  comAmbos: 0,
  semInformacoes: 0,
  falhas: 0,
  erroCodigo: null as string | null,
  erroMensagem: null as string | null,
};
const auditorias: object[] = [];

async function auditar(page: Page, nome: string) {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
  );
  assert.equal(overflow, false, `Overflow em ${nome}`);
  const axe = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  assert.deepEqual(
    axe.violations.map(({ id, nodes }) => ({ id, elementos: nodes.map((n) => n.target) })),
    [],
    `Axe em ${nome}`,
  );
  await page.screenshot({ path: join(saida, `${nome}.png`), fullPage: true });
  auditorias.push({ nome, overflow, violacoes: axe.violations.length });
}

async function aguardarBotaoDisponivel(page: Page) {
  await page.waitForFunction(() => {
    const botao = document.querySelector<HTMLButtonElement>('[data-testid="buscar-informacoes"]');
    return botao !== null && !botao.disabled;
  });
}

const browser = await firefox.launch({ headless: true });
try {
  for (const tema of ['light', 'dark'] as const) {
    for (const viewport of [
      { width: 1440, height: 1000 },
      { width: 390, height: 844 },
    ]) {
      const nome = `${tema}-${viewport.width}`;
      console.log(`Validando ${nome}; evidências em ${saida}`);
      const context = await browser.newContext({ viewport });
      await context.addInitScript((tema) => localStorage.setItem('leads-hunter-theme', tema), tema);
      await context.route('**/*', (route) =>
        new URL(route.request().url()).origin === new URL(baseUrl).origin
          ? route.continue()
          : route.abort(),
      );
      const page = await context.newPage();
      const erros: string[] = [];
      page.on('pageerror', (error) => erros.push(error.message));
      let execucao: typeof execucaoBase | null = null;
      let erroConsulta = false;
      let posts = 0;
      let gets = 0;
      let detalhes = 0;
      await page.route('**/api/**', async (route) => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path === '/api/buscas/42/informacoes') {
          if (request.method() === 'POST') {
            posts++;
            assert.ok(request.postData() === null || request.postData() === 'null');
            execucao = { ...execucaoBase };
            await route.fulfill({ status: 202, json: execucao });
          } else {
            gets++;
            if (erroConsulta) await route.fulfill({ status: 503, json: {} });
            else if (execucao) await route.fulfill({ json: execucao });
            else await route.fulfill({ status: 204 });
          }
        } else if (path === '/api/buscas/42') {
          detalhes++;
          await route.fulfill({ json: { ...resumo, leads } });
        } else if (path === '/api/buscas') {
          await route.fulfill({ json: [resumo] });
        } else {
          throw new Error(`Requisição inesperada: ${request.method()} ${path}`);
        }
      });
      try {
        await page.goto(`${baseUrl}/historico/42`, { waitUntil: 'domcontentloaded' });
        const botao = page.getByTestId('buscar-informacoes');
        await aguardarBotaoDisponivel(page);
        assert.deepEqual(
          await page
            .locator('.historico-detalhe__actions > *')
            .allTextContents()
            .then((textos) => textos.map((texto) => texto.trim())),
          ['Voltar ao histórico', 'Buscar CNPJ', 'Buscar informações'],
        );
        assert.equal(await page.locator('.historico-detalhe__observacoes a').count(), 4);
        assert.equal(await page.locator('.historico-detalhe__observacoes script').count(), 0);
        for (const link of await page.locator('.historico-detalhe__observacoes a').all()) {
          assert.equal(await link.getAttribute('target'), '_blank');
          assert.equal(await link.getAttribute('rel'), 'noopener noreferrer');
        }
        await auditar(page, `${nome}-pronto`);
        await botao.focus();
        await botao.press('Enter');
        await page.waitForFunction(() =>
          document.body.textContent?.includes('Aguardando a vez de pesquisar'),
        );
        assert.equal(await botao.isDisabled(), true);
        assert.equal(posts, 1);
        await auditar(page, `${nome}-pendente`);
        await page.getByRole('link', { name: 'Voltar ao histórico', exact: true }).click();
        await page.waitForURL('**/historico');
        if (nome === 'light-1440') {
          const anteriores = gets;
          await page.waitForTimeout(5200);
          assert.equal(gets, anteriores, 'Polling continuou fora do detalhe');
        }
        execucao = {
          ...execucaoBase,
          status: 'EM_ANDAMENTO',
          progresso: 2,
          processados: 2,
          comInstagram: 2,
          comSite: 1,
          comAmbos: 1,
        };
        await page.getByRole('link', { name: /Abrir busca/ }).click();
        await page.waitForFunction(() => document.body.textContent?.includes('2 de 4 leads'));
        await auditar(page, `${nome}-andamento`);
        const anteriores = detalhes;
        execucao = {
          ...execucaoBase,
          status: 'CONCLUIDA_COM_FALHAS',
          progresso: 4,
          processados: 3,
          comInstagram: 2,
          comSite: 2,
          comAmbos: 1,
          falhas: 1,
          terminadoEm: '2026-09-13T01:10:00',
        };
        await page.waitForFunction(
          () => document.body.textContent?.includes('Busca de informações concluída'),
          undefined,
          { timeout: 10000 },
        );
        await aguardarBotaoDisponivel(page);
        assert.ok(detalhes > anteriores, 'A conclusão não recarregou os dados');
        await auditar(page, `${nome}-parcial`);
        await page.reload();
        await page.waitForFunction(() =>
          document.body.textContent?.includes('Busca de informações concluída'),
        );
        execucao = {
          ...execucaoBase,
          status: 'FALHA',
          falhas: 4,
          progresso: 4,
          erroCodigo: 'PESQUISA_BLOQUEADA',
          erroMensagem: 'O Google bloqueou temporariamente a pesquisa.',
        };
        await page.reload();
        await page
          .getByRole('alert')
          .filter({ hasText: 'Não foi possível concluir a busca de informações' })
          .waitFor();
        assert.equal(await page.locator('tbody tr').count(), 4);
        await auditar(page, `${nome}-falha`);
        erroConsulta = true;
        await page.reload();
        await page.getByRole('button', { name: 'Retomar acompanhamento' }).waitFor();
        assert.equal(await botao.isDisabled(), true);
        await auditar(page, `${nome}-conexao`);
        erroConsulta = false;
        await page.getByRole('button', { name: 'Retomar acompanhamento' }).click();
        await aguardarBotaoDisponivel(page);
        assert.equal(posts, 1, 'Houve POST automático ao restaurar a tela');
        assert.deepEqual(erros, []);
      } finally {
        await context.close();
      }
    }
  }
  writeFileSync(join(saida, 'resultado.json'), JSON.stringify(auditorias, null, 2));
  console.log(JSON.stringify({ status: 'PASSOU', auditorias: auditorias.length, saida }));
} finally {
  await browser.close();
}
