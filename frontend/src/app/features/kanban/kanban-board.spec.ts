import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { StatusFunil } from '../../shared/models/enums.model';
import { LeadResponse } from '../../shared/models/lead.model';
import { KanbanBoard } from './kanban-board';

function criarLead(id: number, status: StatusFunil): LeadResponse {
  return {
    id,
    googlePlaceId: `place-${id}`,
    nome: `Lead ${id}`,
    categoria: 'MERCADO',
    enderecoFormatado: null,
    telefone: null,
    telefoneNormalizado: null,
    whatsappUrl: null,
    latitude: null,
    longitude: null,
    ratingGoogle: null,
    totalReviews: null,
    score: id,
    temperatura: 'FRIO',
    status,
    observacoes: null,
    ultimoContatoEm: null,
    criadoEm: null,
    atualizadoEm: null,
  };
}

describe('KanbanBoard', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [KanbanBoard] }));

  async function renderizar(leads: readonly LeadResponse[], idsEmMovimento = new Set<number>()) {
    const fixture = TestBed.createComponent(KanbanBoard);
    fixture.componentRef.setInput('leads', leads);
    fixture.componentRef.setInput('idsEmMovimento', idsEmMovimento);
    await fixture.whenStable();
    return fixture;
  }

  it('renderiza as cinco colunas, suas contagens e cada lead uma única vez', async () => {
    const fixture = await renderizar([
      criarLead(1, 'NOVO'),
      criarLead(2, 'NOVO'),
      criarLead(3, 'QUALIFICADO'),
      criarLead(4, 'GANHO'),
    ]);
    const colunas = fixture.nativeElement.querySelectorAll('.kanban-column');
    const cards = fixture.nativeElement.querySelectorAll('.lead-card');

    expect(colunas).toHaveLength(5);
    expect(cards).toHaveLength(4);
    expect(fixture.nativeElement.querySelector('[data-status="NOVO"]')?.textContent).toContain('2');
    expect(
      fixture.nativeElement.querySelector('[data-status="QUALIFICADO"]')?.textContent,
    ).toContain('1');
    expect(fixture.nativeElement.querySelector('[data-status="CONTATADO"]')?.textContent).toContain(
      '0',
    );
    expect(fixture.nativeElement.querySelector('[data-status="GANHO"]')?.textContent).toContain(
      '1',
    );
    expect(fixture.nativeElement.querySelector('[data-status="PERDIDO"]')?.textContent).toContain(
      '0',
    );
  });

  it('diferencia coluna vazia do estado vazio da lista completa', async () => {
    const fixture = await renderizar([criarLead(1, 'NOVO')]);
    const novo = fixture.nativeElement.querySelector('[data-status="NOVO"]') as HTMLElement;
    const perdido = fixture.nativeElement.querySelector('[data-status="PERDIDO"]') as HTMLElement;

    expect(novo.textContent).not.toContain('Nenhum lead nesta etapa');
    expect(perdido.textContent).toContain('Nenhum lead nesta etapa');
    expect(fixture.nativeElement.textContent).not.toContain('Nenhum lead encontrado');
  });

  it('oferece uma região nomeada e focável para rolagem horizontal', async () => {
    const fixture = await renderizar([criarLead(1, 'NOVO')]);
    const viewport = fixture.nativeElement.querySelector('.kanban-board__viewport') as HTMLElement;

    expect(viewport.getAttribute('role')).toBe('region');
    expect(viewport.getAttribute('aria-label')).toContain('Quadro Kanban');
    expect(viewport.tabIndex).toBe(0);
  });

  it('conecta as colunas ao CDK e encaminha a alternativa de movimento por botão', async () => {
    const lead = criarLead(1, 'NOVO');
    const fixture = await renderizar([lead]);
    const mudancas: Array<{ lead: LeadResponse; status: StatusFunil }> = [];
    fixture.componentInstance.mudancaStatusSolicitada.subscribe((mudanca) =>
      mudancas.push(mudanca),
    );

    expect(fixture.nativeElement.querySelectorAll('.cdk-drop-list')).toHaveLength(5);
    expect(fixture.nativeElement.querySelectorAll('.cdk-drag')).toHaveLength(1);

    const botao = fixture.nativeElement.querySelector(
      '[aria-label="Mover Lead 1 para Qualificado"]',
    ) as HTMLButtonElement;
    botao.click();

    expect(mudancas).toEqual([{ lead, status: 'QUALIFICADO' }]);
  });

  it('marca e bloqueia somente o card com atualização em andamento', async () => {
    const fixture = await renderizar([criarLead(1, 'NOVO'), criarLead(2, 'NOVO')], new Set([1]));
    const cards = fixture.nativeElement.querySelectorAll('.lead-card');
    const drags = fixture.nativeElement.querySelectorAll('.cdk-drag');

    expect(cards[0].getAttribute('aria-busy')).toBe('true');
    expect(cards[1].getAttribute('aria-busy')).toBe('false');
    expect(drags[0].classList).toContain('cdk-drag-disabled');
    expect(drags[1].classList).not.toContain('cdk-drag-disabled');
  });
});
