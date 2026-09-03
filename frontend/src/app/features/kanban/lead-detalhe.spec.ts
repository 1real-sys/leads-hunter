import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_ROUTES } from '../../core/api/api-routes';
import { ApiErrorResponse } from '../../shared/models/api-error-response.model';
import { LeadResponse } from '../../shared/models/lead.model';
import { LeadDetalhe } from './lead-detalhe';

const LEAD_COMPLETO: LeadResponse = {
  id: 7,
  googlePlaceId: 'place-7',
  nome: 'Padaria Central',
  categoria: 'PADARIA',
  enderecoFormatado: 'Rua Central, 100, Centro, Vitória - ES',
  telefone: '(27) 3333-4444',
  telefoneNormalizado: '552733334444',
  whatsappUrl: 'https://wa.me/552733334444',
  latitude: -20.3155,
  longitude: -40.3128,
  ratingGoogle: 4.8,
  totalReviews: 120,
  score: 82,
  temperatura: 'QUENTE',
  status: 'QUALIFICADO',
  observacoes: 'Pediu retorno na próxima semana.',
  ultimoContatoEm: '2026-09-01T14:00:00',
  criadoEm: '2026-08-31T10:30:00',
  atualizadoEm: '2026-09-01T14:00:00',
};

describe('LeadDetalhe', () => {
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LeadDetalhe],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function renderizar(lead: LeadResponse) {
    const fixture = TestBed.createComponent(LeadDetalhe);
    fixture.componentRef.setInput('lead', lead);
    await fixture.whenStable();
    return fixture;
  }

  function preencherCampo(
    fixture: Awaited<ReturnType<typeof renderizar>>,
    seletor: string,
    valor: string,
  ): void {
    const campo = fixture.nativeElement.querySelector(seletor) as HTMLInputElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input', { bubbles: true }));
  }

  function enviarFormulario(fixture: Awaited<ReturnType<typeof renderizar>>): void {
    const form = fixture.nativeElement.querySelector(
      '.lead-detalhe-panel__formulario',
    ) as HTMLFormElement;
    form.dispatchEvent(new SubmitEvent('submit', { bubbles: true, cancelable: true }));
  }

  it('apresenta um diálogo rotulado com os dados completos do lead', async () => {
    const fixture = await renderizar(LEAD_COMPLETO);
    const painel = fixture.nativeElement.querySelector(
      '.lead-detalhe-panel',
    ) as HTMLElement;
    const conteudo = fixture.nativeElement.textContent as string;

    expect(painel.getAttribute('role')).toBe('dialog');
    expect(painel.getAttribute('aria-modal')).toBe('true');
    expect(painel.getAttribute('aria-labelledby')).toBe('detalhe-lead-7-titulo');
    expect(conteudo).toContain('Padaria Central');
    expect(conteudo).toContain('Padaria');
    expect(conteudo).toContain('Qualificado');
    expect(conteudo).toContain('Quente');
    expect(conteudo).toContain('Score');
    expect(conteudo).toContain('82');
    expect(conteudo).toContain('Rua Central, 100');
    expect(conteudo).toContain('(27) 3333-4444');
    expect(conteudo).toContain('-20.3155');
    expect(conteudo).toContain('Nota Google');
    expect(conteudo).toContain('Avaliações');
    expect(conteudo).toContain('120');
    expect(conteudo).toContain('Pediu retorno na próxima semana.');
    expect(conteudo).toContain('31/08/2026');
  });

  it('abre o WhatsApp em nova aba com proteção somente quando há URL do backend', async () => {
    const fixture = await renderizar(LEAD_COMPLETO);
    const link = fixture.nativeElement.querySelector(
      '.lead-detalhe-panel__whatsapp',
    ) as HTMLAnchorElement;

    expect(link).not.toBeNull();
    expect(link.href).toBe('https://wa.me/552733334444');
    expect(link.target).toBe('_blank');
    expect(link.rel).toContain('noopener');
    expect(link.rel).toContain('noreferrer');
  });

  it('indica WhatsApp indisponível quando não há telefone válido e nunca inventa link', async () => {
    const fixture = await renderizar({ ...LEAD_COMPLETO, whatsappUrl: null });
    const conteudo = fixture.nativeElement.textContent as string;

    expect(fixture.nativeElement.querySelector('.lead-detalhe-panel__whatsapp')).toBeNull();
    expect(conteudo).toContain('WhatsApp indisponível');
  });

  it('omite campos externos nulos e informa ausência comercial sem inventar dados', async () => {
    const fixture = await renderizar({
      ...LEAD_COMPLETO,
      nome: null,
      categoria: null,
      enderecoFormatado: null,
      telefone: null,
      latitude: null,
      longitude: null,
      ratingGoogle: null,
      totalReviews: null,
      score: null,
      temperatura: null,
      status: null,
      observacoes: null,
      ultimoContatoEm: null,
    });
    const conteudo = fixture.nativeElement.textContent as string;

    expect(conteudo).toContain('Lead #7');
    expect(conteudo).toContain('Sem etapa');
    expect(conteudo).not.toContain('Rua Central');
    expect(conteudo).not.toContain('Nota Google');
    expect(conteudo).toContain('Nenhuma observação registrada.');
    expect(fixture.nativeElement.querySelector('.lead-detalhe-panel__dados')).toBeNull();
  });

  it('informa que nunca houve contato quando ainda não existe data', async () => {
    const fixture = await renderizar({ ...LEAD_COMPLETO, ultimoContatoEm: null });

    expect(fixture.nativeElement.textContent).toContain('Nunca registrado');
  });

  it('emite o evento de fechamento ao clicar no botão Fechar', async () => {
    const fixture = await renderizar(LEAD_COMPLETO);
    let fechou = false;
    fixture.componentInstance.fechado.subscribe(() => (fechou = true));

    const botaoFechar = fixture.nativeElement.querySelector(
      '.lead-detalhe-panel__fechar',
    ) as HTMLButtonElement;
    expect(botaoFechar.getAttribute('aria-label')).toContain('Padaria Central');
    botaoFechar.click();

    expect(fechou).toBe(true);
  });

  it('emite o evento de fechamento ao pressionar Escape no painel', async () => {
    const fixture = await renderizar(LEAD_COMPLETO);
    let fechou = false;
    fixture.componentInstance.fechado.subscribe(() => (fechou = true));

    const painel = fixture.nativeElement.querySelector(
      '.lead-detalhe-panel',
    ) as HTMLElement;
    painel.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

    expect(fechou).toBe(true);
  });

  it('move o foco para dentro do painel ao abrir', async () => {
    await renderizar(LEAD_COMPLETO);
    const painel = document.activeElement?.closest('.lead-detalhe-panel');

    expect(painel).not.toBeNull();
  });

  it('inicia a edição pré-preenchida com os valores comerciais atuais', async () => {
    const fixture = await renderizar(LEAD_COMPLETO);
    const editar = [...fixture.nativeElement.querySelectorAll('button')].find(
      (botao: HTMLButtonElement) => botao.textContent?.trim() === 'Editar',
    ) as HTMLButtonElement;
    editar.click();
    await fixture.whenStable();

    const textarea = fixture.nativeElement.querySelector(
      '#detalhe-observacoes',
    ) as HTMLTextAreaElement;
    const contato = fixture.nativeElement.querySelector(
      '#detalhe-ultimo-contato',
    ) as HTMLInputElement;

    expect(textarea.value).toBe('Pediu retorno na próxima semana.');
    expect(contato.value).toBe('2026-09-01T14:00');
    expect(fixture.nativeElement.textContent).toContain(
      'Limpar este campo não remove o último contato',
    );
  });

  it('salva somente as observações alteradas e emite o lead confirmado', async () => {
    const fixture = await renderizar(LEAD_COMPLETO);
    const confirmados: LeadResponse[] = [];
    fixture.componentInstance.leadAtualizado.subscribe((lead) => confirmados.push(lead));

    const editar = [...fixture.nativeElement.querySelectorAll('button')].find(
      (botao: HTMLButtonElement) => botao.textContent?.trim() === 'Editar',
    ) as HTMLButtonElement;
    editar.click();
    await fixture.whenStable();

    preencherCampo(fixture, '#detalhe-observacoes', 'Dono pediu retorno na sexta-feira.');
    await fixture.whenStable();
    expect(fixture.componentInstance['observacoesAlteradas']()).toBe(true);
    expect(fixture.componentInstance['temAlteracaoSalvavel']()).toBe(true);
    enviarFormulario(fixture);
    await fixture.whenStable();

    const request = httpTesting.expectOne(API_ROUTES.lead(7));
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ observacoes: 'Dono pediu retorno na sexta-feira.' });

    const confirmado: LeadResponse = {
      ...LEAD_COMPLETO,
      observacoes: 'Dono pediu retorno na sexta-feira.',
    };
    request.flush(confirmado);
    await fixture.whenStable();

    expect(confirmados).toEqual([confirmado]);
    expect(fixture.componentInstance['editando']()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Alterações salvas.');
  });

  it('salva somente o último contato quando apenas ele muda', async () => {
    const fixture = await renderizar(LEAD_COMPLETO);
    const editar = [...fixture.nativeElement.querySelectorAll('button')].find(
      (botao: HTMLButtonElement) => botao.textContent?.trim() === 'Editar',
    ) as HTMLButtonElement;
    editar.click();
    await fixture.whenStable();

    preencherCampo(fixture, '#detalhe-ultimo-contato', '2026-09-02T09:30');
    await fixture.whenStable();
    enviarFormulario(fixture);
    await fixture.whenStable();

    const request = httpTesting.expectOne(API_ROUTES.lead(7));
    expect(request.request.body).toEqual({ ultimoContatoEm: '2026-09-02T09:30' });
    const confirmado: LeadResponse = {
      ...LEAD_COMPLETO,
      ultimoContatoEm: '2026-09-02T09:30:00',
    };
    request.flush(confirmado);
    fixture.componentRef.setInput('lead', confirmado);
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('02/09/2026');
  });

  it('envia juntas as alterações de observações e contato quando ambas mudam', async () => {
    const fixture = await renderizar(LEAD_COMPLETO);
    const editar = [...fixture.nativeElement.querySelectorAll('button')].find(
      (botao: HTMLButtonElement) => botao.textContent?.trim() === 'Editar',
    ) as HTMLButtonElement;
    editar.click();
    await fixture.whenStable();

    preencherCampo(fixture, '#detalhe-observacoes', 'Contato realizado.');
    preencherCampo(fixture, '#detalhe-ultimo-contato', '2026-09-03T08:00');
    await fixture.whenStable();
    enviarFormulario(fixture);
    await fixture.whenStable();

    const request = httpTesting.expectOne(API_ROUTES.lead(7));
    expect(request.request.body).toEqual({
      observacoes: 'Contato realizado.',
      ultimoContatoEm: '2026-09-03T08:00',
    });
    request.flush({ ...LEAD_COMPLETO, observacoes: 'Contato realizado.' });
    await fixture.whenStable();
  });

  it('permite limpar as observações com string vazia', async () => {
    const fixture = await renderizar(LEAD_COMPLETO);
    const editar = [...fixture.nativeElement.querySelectorAll('button')].find(
      (botao: HTMLButtonElement) => botao.textContent?.trim() === 'Editar',
    ) as HTMLButtonElement;
    editar.click();
    await fixture.whenStable();

    preencherCampo(fixture, '#detalhe-observacoes', '');
    await fixture.whenStable();
    enviarFormulario(fixture);
    await fixture.whenStable();

    const request = httpTesting.expectOne(API_ROUTES.lead(7));
    expect(request.request.body).toEqual({ observacoes: '' });
    request.flush({ ...LEAD_COMPLETO, observacoes: null });
    await fixture.whenStable();
  });

  it('bloqueia o envio sem alteração e o libera ao alterar um campo', async () => {
    const fixture = await renderizar(LEAD_COMPLETO);
    const editar = [...fixture.nativeElement.querySelectorAll('button')].find(
      (botao: HTMLButtonElement) => botao.textContent?.trim() === 'Editar',
    ) as HTMLButtonElement;
    editar.click();
    await fixture.whenStable();

    const salvar = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    expect(salvar.disabled).toBe(true);

    preencherCampo(fixture, '#detalhe-observacoes', 'Nova observação.');
    await fixture.whenStable();
    expect(salvar.disabled).toBe(false);
  });

  it('não envia quando o usuário apenas limpa o último contato, explicando a limitação', async () => {
    const fixture = await renderizar(LEAD_COMPLETO);
    const editar = [...fixture.nativeElement.querySelectorAll('button')].find(
      (botao: HTMLButtonElement) => botao.textContent?.trim() === 'Editar',
    ) as HTMLButtonElement;
    editar.click();
    await fixture.whenStable();

    preencherCampo(fixture, '#detalhe-ultimo-contato', '');
    await fixture.whenStable();
    enviarFormulario(fixture);
    await fixture.whenStable();

    const salvar = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    expect(salvar.disabled).toBe(true);
    httpTesting.expectNone(API_ROUTES.lead(7));
  });

  it('preserva o texto digitado e mantém a edição aberta quando o PATCH falha', async () => {
    const fixture = await renderizar(LEAD_COMPLETO);
    const editar = [...fixture.nativeElement.querySelectorAll('button')].find(
      (botao: HTMLButtonElement) => botao.textContent?.trim() === 'Editar',
    ) as HTMLButtonElement;
    editar.click();
    await fixture.whenStable();

    preencherCampo(fixture, '#detalhe-observacoes', 'Texto que não pode se perder.');
    await fixture.whenStable();
    enviarFormulario(fixture);
    await fixture.whenStable();

    httpTesting.expectOne(API_ROUTES.lead(7)).flush(
      {
        timestamp: '2026-09-03T12:00:00Z',
        status: 500,
        codigo: 'ERRO_INTERNO',
        mensagem: 'Não foi possível atualizar o lead.',
        path: API_ROUTES.lead(7),
      } satisfies ApiErrorResponse,
      { status: 500, statusText: 'Internal Server Error' },
    );
    await fixture.whenStable();

    const textarea = fixture.nativeElement.querySelector(
      '#detalhe-observacoes',
    ) as HTMLTextAreaElement;
    expect(fixture.componentInstance['editando']()).toBe(true);
    expect(textarea.value).toBe('Texto que não pode se perder.');
    expect(fixture.nativeElement.textContent).toContain(
      'Não foi possível atualizar o lead.',
    );
  });

  it('avisa sobre alterações não salvas ao tentar fechar e só fecha após descartar', async () => {
    const fixture = await renderizar(LEAD_COMPLETO);
    let fechou = false;
    fixture.componentInstance.fechado.subscribe(() => (fechou = true));

    const editar = [...fixture.nativeElement.querySelectorAll('button')].find(
      (botao: HTMLButtonElement) => botao.textContent?.trim() === 'Editar',
    ) as HTMLButtonElement;
    editar.click();
    await fixture.whenStable();

    preencherCampo(fixture, '#detalhe-observacoes', 'Alteração não salva.');
    await fixture.whenStable();

    const painel = fixture.nativeElement.querySelector('.lead-detalhe-panel') as HTMLElement;
    painel.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await fixture.whenStable();

    expect(fechou).toBe(false);
    expect(fixture.nativeElement.textContent).toContain(
      'Há alterações ainda não salvas. Salve ou cancele a edição antes de fechar.',
    );

    const cancelar = [...fixture.nativeElement.querySelectorAll('button')].find(
      (botao: HTMLButtonElement) => botao.textContent?.trim() === 'Cancelar',
    ) as HTMLButtonElement;
    cancelar.click();
    await fixture.whenStable();

    painel.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await fixture.whenStable();

    expect(fechou).toBe(true);
  });
});
