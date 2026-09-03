import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
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
  beforeEach(() => TestBed.configureTestingModule({ imports: [LeadDetalhe] }));

  async function renderizar(lead: LeadResponse) {
    const fixture = TestBed.createComponent(LeadDetalhe);
    fixture.componentRef.setInput('lead', lead);
    await fixture.whenStable();
    return fixture;
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
    expect(fixture.nativeElement.querySelector('.lead-detalhe-panel__dados')).not.toBeNull();
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
});
