import { firefox } from 'playwright';

const BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:4200';
const API_MODE = process.env.E2E_API_MODE ?? 'mock';
const IS_MOCK = API_MODE === 'mock';

const MOCK_LEAD = {
  id: 7,
  googlePlaceId: 'fe17-smoke-place-001',
  nome: 'Padaria Fluxo Integrado',
  categoria: 'PADARIA',
  enderecoFormatado: 'Rua do Fluxo, 17',
  telefone: '+55 27 99999-0000',
  telefoneNormalizado: '5527999990000',
  whatsappUrl: 'https://wa.me/5527999990000',
  latitude: -20.3155,
  longitude: -40.3128,
  ratingGoogle: 4.8,
  totalReviews: 80,
  score: 82,
  temperatura: 'QUENTE',
  status: 'NOVO',
  observacoes: null,
  ultimoContatoEm: null,
  criadoEm: '2026-09-05T10:00:00',
  atualizadoEm: '2026-09-05T10:00:00',
};

const MOCK_STATE = {
  bloqueios: [],
  lead: { ...MOCK_LEAD },
  busca: {
    id: 1,
    enderecoBase: 'Centro de Vitória',
    latitude: -20.3155,
    longitude: -40.3128,
    raioKm: 3,
    categorias: ['PADARIA'],
    totalEncontrados: 2,
    criadoEm: '2026-09-05T10:00:00',
  },
};

const requests = [];

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function parseBody(request) {
  const body = request.postData();
  if (body === null || body.trim() === '') {
    return null;
  }

  return JSON.parse(body);
}

function paginaDoLead(status, pagina) {
  const leads = MOCK_STATE.lead.status === status && pagina === 0 ? [MOCK_STATE.lead] : [];
  return {
    leads,
    pagina,
    tamanho: 25,
    totalElementos: leads.length,
    totalPaginas: leads.length === 0 ? 0 : 1,
  };
}

async function responderJson(route, corpo, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(corpo),
  });
}

async function mockApi(route) {
  const request = route.request();
  const url = new URL(request.url());
  const body = parseBody(request);
  requests.push({ method: request.method(), path: url.pathname, query: url.search, body });

  if (!IS_MOCK) {
    await route.continue();
    return;
  }

  if (request.method() === 'GET' && url.pathname === '/api/bloqueios') {
    await responderJson(route, MOCK_STATE.bloqueios);
    return;
  }

  if (request.method() === 'POST' && url.pathname === '/api/bloqueios') {
    const bloqueio = {
      id: 1,
      termo: body?.termo,
      criadoEm: '2026-09-08T12:00:00',
    };
    MOCK_STATE.bloqueios.push(bloqueio);
    await responderJson(route, bloqueio, 201);
    return;
  }

  if (request.method() === 'DELETE' && url.pathname === '/api/bloqueios/1') {
    MOCK_STATE.bloqueios = [];
    await route.fulfill({ status: 204 });
    return;
  }

  if (request.method() === 'POST' && url.pathname === '/api/buscas') {
    await responderJson(route, {
      ...MOCK_STATE.busca,
      totalBloqueados: MOCK_STATE.bloqueios.length > 0 ? 1 : 0,
      categorias: body?.categorias ?? MOCK_STATE.busca.categorias,
      leads: [
        {
          id: MOCK_STATE.lead.id,
          nome: MOCK_STATE.lead.nome,
          categoria: MOCK_STATE.lead.categoria,
          enderecoFormatado: MOCK_STATE.lead.enderecoFormatado,
          telefone: MOCK_STATE.lead.telefone,
          whatsappUrl: MOCK_STATE.lead.whatsappUrl,
          score: MOCK_STATE.lead.score,
          temperatura: MOCK_STATE.lead.temperatura,
        },
      ],
    });
    return;
  }

  if (request.method() === 'GET' && url.pathname === '/api/leads/pagina') {
    await responderJson(
      route,
      paginaDoLead(url.searchParams.get('status'), Number(url.searchParams.get('page') ?? 0)),
    );
    return;
  }

  if (request.method() === 'PATCH' && url.pathname === `/api/leads/${MOCK_STATE.lead.id}`) {
    Object.assign(MOCK_STATE.lead, body ?? {});
    MOCK_STATE.lead.atualizadoEm = '2026-09-05T11:00:00';
    await responderJson(route, { ...MOCK_STATE.lead });
    return;
  }

  if (request.method() === 'GET' && url.pathname === '/api/leads') {
    await responderJson(route, [MOCK_STATE.lead]);
    return;
  }

  if (request.method() === 'GET' && url.pathname === '/api/buscas') {
    await responderJson(route, [{ ...MOCK_STATE.busca }]);
    return;
  }

  if (request.method() === 'GET' && url.pathname === `/api/buscas/${MOCK_STATE.busca.id}`) {
    await responderJson(route, {
      ...MOCK_STATE.busca,
      leads: [
        {
          id: MOCK_STATE.lead.id,
          nome: MOCK_STATE.lead.nome,
          categoria: MOCK_STATE.lead.categoria,
          enderecoFormatado: MOCK_STATE.lead.enderecoFormatado,
          telefone: MOCK_STATE.lead.telefone,
          whatsappUrl: MOCK_STATE.lead.whatsappUrl,
          scoreNaBusca: MOCK_STATE.lead.score,
          temperaturaNaBusca: MOCK_STATE.lead.temperatura,
          status: MOCK_STATE.lead.status,
          observacoes: MOCK_STATE.lead.observacoes,
          ultimoContatoEm: MOCK_STATE.lead.ultimoContatoEm,
        },
      ],
    });
    return;
  }

  if (request.method() === 'GET' && url.pathname === '/api/exportacao/leads.csv') {
    await route.fulfill({
      contentType: 'text/csv;charset=UTF-8',
      headers: { 'Content-Disposition': 'attachment; filename="leads.csv"' },
      body: `id,nome,status\n${MOCK_STATE.lead.id},${MOCK_STATE.lead.nome},${MOCK_STATE.lead.status}\n`,
    });
    return;
  }

  if (request.method() === 'GET' && url.pathname === '/api/exportacao/leads.xlsx') {
    await route.fulfill({
      contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      headers: { 'Content-Disposition': 'attachment; filename="leads.xlsx"' },
      body: Buffer.from([0x50, 0x4b, 0x03, 0x04]),
    });
    return;
  }

  await responderJson(route, { codigo: 'SMOKE_ROTA_NAO_MOCKADA' }, 404);
}

async function aguardar(locator) {
  await locator.first().waitFor({ state: 'visible', timeout: 30000 });
}

async function executarFluxo(page) {
  if (IS_MOCK) {
    await page.goto(`${BASE_URL}/bloqueios`, {
      waitUntil: 'domcontentloaded',
      timeout: 30000,
    });
    await aguardar(page.locator('.bloqueios-page'));
    await aguardar(page.getByText('Nenhum termo cadastrado'));
    await page.locator('#termo-bloqueado').fill('Supermercados BH');
    await page.getByRole('button', { name: 'Adicionar bloqueio', exact: true }).click();
    await aguardar(page.getByText('Supermercados BH', { exact: true }));
    assert(
      requests.some(
        (item) =>
          item.method === 'POST' &&
          item.path === '/api/bloqueios' &&
          item.body?.termo === 'Supermercados BH',
      ),
      'O cadastro do bloqueio não enviou o termo esperado.',
    );
    await page.getByRole('link', { name: /^Busca/ }).click();
    await page.waitForURL('**/busca');
  } else {
    await page.goto(`${BASE_URL}/busca`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  }
  await aguardar(page.locator('.busca-page'));

  await page.locator('#endereco-base').fill('Centro de Vitória');
  await page.locator('[data-categoria="PADARIA"]').check();
  await page.getByRole('button', { name: 'Buscar leads', exact: true }).click();
  await aguardar(page.locator('.resultados'));

  const resultadoTexto = await page.locator('.resultados').innerText();
  assert(
    resultadoTexto.includes(IS_MOCK ? 'Padaria Fluxo Integrado' : 'lead'),
    'Resultados não apareceram após a busca.',
  );

  if (IS_MOCK) {
    const buscaRequest = requests.find(
      (item) => item.method === 'POST' && item.path === '/api/buscas',
    );
    assert(
      buscaRequest?.body?.categorias?.includes('PADARIA'),
      'A busca não enviou a categoria esperada.',
    );
    assert(
      resultadoTexto.includes('1 resultado ignorado pelos bloqueios cadastrados.'),
      'O resumo não informou o resultado ignorado pela blacklist.',
    );
  }

  await page.getByRole('link', { name: 'Seguir para o Kanban', exact: true }).click();
  await page.waitForURL('**/kanban');
  await aguardar(page.locator('.kanban-page'));
  await aguardar(page.locator('.lead-card'));

  const primeiroCard = page.locator('.lead-card').first();
  const nomeDoCard = await primeiroCard
    .locator('.lead-card__title-button')
    .getAttribute('aria-label');
  assert(nomeDoCard !== null, 'O card do Kanban não possui nome acessível.');

  await primeiroCard.locator('.lead-card__title-button').click();
  await aguardar(page.getByRole('dialog'));
  await page.getByRole('button', { name: 'Editar', exact: true }).click();
  await page.locator('#detalhe-observacoes').fill('Validado no fluxo FE-17.');
  await page.locator('#detalhe-ultimo-contato').fill('2026-09-05T11:00');
  await page.getByRole('button', { name: 'Salvar alterações', exact: true }).click();
  await aguardar(page.locator('.lead-detalhe-panel__feedback--sucesso'));

  if (IS_MOCK) {
    const patchComercial = requests.find(
      (item) =>
        item.method === 'PATCH' &&
        item.path === `/api/leads/${MOCK_STATE.lead.id}` &&
        item.body?.observacoes,
    );
    assert(
      patchComercial?.body?.ultimoContatoEm === '2026-09-05T11:00',
      'A edição comercial não enviou a data esperada.',
    );
  }

  await page.getByRole('button', { name: /Fechar detalhes de/ }).click();
  await page.reload({ waitUntil: 'domcontentloaded' });
  await aguardar(page.locator('.kanban-page'));
  await aguardar(page.locator('.lead-card'));
  await page.locator('.lead-card__title-button').first().click();
  await aguardar(page.getByRole('dialog'));
  const dadosPersistidos = await page.getByRole('dialog').innerText();
  assert(
    dadosPersistidos.includes('Validado no fluxo FE-17.'),
    'O reload não preservou as observações comerciais.',
  );
  await page.getByRole('button', { name: /Fechar detalhes de/ }).click();

  const movimento = page.locator('button[aria-label^="Mover "]').first();
  if ((await movimento.count()) > 0) {
    const ariaLabel = await movimento.getAttribute('aria-label');
    await movimento.click();
    if (IS_MOCK) {
      await aguardar(page.locator('[data-status="QUALIFICADO"] .lead-card'));
      assert(
        ariaLabel?.includes('Qualificado'),
        'O controle de etapa não ofereceu o destino esperado.',
      );
    }
  }

  await page.getByRole('link', { name: /^Histórico/ }).click();
  await page.waitForURL('**/historico');
  await aguardar(page.locator('.historico-page'));
  await aguardar(page.locator('.historico-page__table'));
  assert((await page.locator('tbody tr').count()) > 0, 'O histórico não exibiu a busca realizada.');

  const abrirBusca = page.getByRole('link', { name: /Abrir busca/ }).first();
  await abrirBusca.click();
  await page.waitForURL('**/historico/*');
  await aguardar(page.locator('.historico-detalhe'));
  assert(
    (await page.locator('.historico-detalhe').innerText()).includes('Observações atuais'),
    'O detalhe do histórico não carregou os dados comerciais.',
  );

  const whatsapp = page.locator('a[href^="https://wa.me/"]');
  assert((await whatsapp.count()) > 0, 'O link manual de WhatsApp não apareceu no histórico.');
  assert(
    (await whatsapp.first().getAttribute('target')) === '_blank',
    'O WhatsApp não está configurado para abertura manual em nova aba.',
  );

  await page.getByRole('link', { name: /^Kanban/ }).click();
  await page.waitForURL('**/kanban');
  await aguardar(page.locator('.kanban-page'));
  const downloadPromise = page.waitForEvent('download', { timeout: 30000 });
  await page.getByRole('button', { name: 'Baixar CSV', exact: true }).click();
  const download = await downloadPromise;
  assert(
    download.suggestedFilename().endsWith('.csv'),
    'A exportação CSV não iniciou um download.',
  );

  if (IS_MOCK) {
    await page.getByRole('link', { name: /^Bloqueios/ }).click();
    await page.waitForURL('**/bloqueios');
    await aguardar(page.locator('.bloqueios-page__list'));
    await page
      .getByRole('button', { name: 'Remover bloqueio Supermercados BH', exact: true })
      .click();
    await aguardar(page.getByText('Nenhum termo cadastrado'));
    assert(
      requests.some((item) => item.method === 'DELETE' && item.path === '/api/bloqueios/1'),
      'A remoção do bloqueio não chamou o endpoint esperado.',
    );
  }
}

const browser = await firefox.launch({ headless: true });
const context = await browser.newContext({
  acceptDownloads: true,
  viewport: { width: 1440, height: 1200 },
});
const page = await context.newPage();

try {
  await page.route('**/api/**', mockApi);
  await executarFluxo(page);
  console.log(
    JSON.stringify({ status: 'PASSOU', modo: API_MODE, requisicoes: requests.length }, null, 2),
  );
} catch (error) {
  console.error(
    JSON.stringify({ status: 'FALHOU', modo: API_MODE, mensagem: String(error) }, null, 2),
  );
  process.exitCode = 1;
} finally {
  await browser.close();
}
