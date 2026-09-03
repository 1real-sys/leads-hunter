import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { LeadResponse } from '../../shared/models/lead.model';
import { LeadCard } from './lead-card';

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
  observacoes: null,
  ultimoContatoEm: null,
  criadoEm: '2026-08-31T10:30:00',
  atualizadoEm: '2026-08-31T10:30:00',
};

describe('LeadCard', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [LeadCard] }));

  async function renderizar(lead: LeadResponse) {
    const fixture = TestBed.createComponent(LeadCard);
    fixture.componentRef.setInput('lead', lead);
    await fixture.whenStable();
    return fixture;
  }

  it('apresenta os dados comerciais compactos com status e temperatura textuais', async () => {
    const fixture = await renderizar(LEAD_COMPLETO);
    const conteudo = fixture.nativeElement.textContent as string;
    const card = fixture.nativeElement.querySelector('.lead-card') as HTMLElement;
    const endereco = fixture.nativeElement.querySelector('.lead-card__address') as HTMLElement;

    expect(conteudo).toContain('Padaria Central');
    expect(conteudo).toContain('Padaria');
    expect(conteudo).toContain('Rua Central, 100');
    expect(conteudo).toContain('(27) 3333-4444');
    expect(conteudo).toContain('Nota');
    expect(conteudo).toContain('4.8');
    expect(conteudo).toContain('Avaliações');
    expect(conteudo).toContain('120');
    expect(conteudo).toContain('Score');
    expect(conteudo).toContain('82');
    expect(conteudo).toContain('Quente');
    expect(conteudo).toContain('Qualificado');
    expect(endereco.title).toBe(LEAD_COMPLETO.enderecoFormatado);
    expect(card.getAttribute('aria-labelledby')).toBe('lead-7-title');
  });

  it('omite campos nulos sem criar dados substitutos enganosos', async () => {
    const fixture = await renderizar({
      ...LEAD_COMPLETO,
      id: 99,
      nome: null,
      categoria: null,
      enderecoFormatado: null,
      telefone: null,
      ratingGoogle: null,
      totalReviews: null,
      score: null,
      temperatura: null,
      status: null,
    });
    const conteudo = fixture.nativeElement.textContent as string;

    expect(conteudo).toContain('Lead #99');
    expect(conteudo).toContain('Sem etapa');
    expect(fixture.nativeElement.querySelector('.lead-card__address')).toBeNull();
    expect(fixture.nativeElement.querySelector('.lead-card__phone')).toBeNull();
    expect(fixture.nativeElement.querySelector('.lead-card__metrics')).toBeNull();
    expect(fixture.nativeElement.querySelector('.lead-card__temperature')).toBeNull();
  });

  it('identifica como sem etapa um status desconhecido recebido em runtime', async () => {
    const fixture = await renderizar({
      ...LEAD_COMPLETO,
      status: 'ARQUIVADO' as LeadResponse['status'],
    });

    expect(fixture.nativeElement.textContent).toContain('Sem etapa');
  });

  it('oferece destinos adjacentes por controles textuais acionáveis pelo teclado', async () => {
    const fixture = await renderizar(LEAD_COMPLETO);
    const destinos: string[] = [];
    fixture.componentInstance.mudancaStatusSolicitada.subscribe((status) => destinos.push(status));
    const botoes = [...fixture.nativeElement.querySelectorAll('button')] as HTMLButtonElement[];

    expect(botoes.map((botao) => botao.textContent?.trim())).toEqual([
      'Para Novo',
      'Para Contatado',
    ]);
    expect(botoes[1].getAttribute('aria-label')).toContain('Padaria Central para Contatado');

    botoes[1].click();

    expect(destinos).toEqual(['CONTATADO']);
  });

  it('bloqueia drag e controles enquanto a etapa está sendo salva', async () => {
    const fixture = TestBed.createComponent(LeadCard);
    fixture.componentRef.setInput('lead', LEAD_COMPLETO);
    fixture.componentRef.setInput('movendo', true);
    await fixture.whenStable();
    const card = fixture.nativeElement.querySelector('.lead-card') as HTMLElement;
    const handle = fixture.nativeElement.querySelector('.lead-card__drag-handle') as HTMLElement;

    expect(card.getAttribute('aria-busy')).toBe('true');
    expect(handle.classList).toContain('lead-card__drag-handle--disabled');
    expect(fixture.nativeElement.querySelectorAll('button')).toHaveLength(0);
    expect(fixture.nativeElement.textContent).toContain('Salvando etapa');
  });
});
