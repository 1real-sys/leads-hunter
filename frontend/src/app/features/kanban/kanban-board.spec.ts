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

  async function renderizar(leads: readonly LeadResponse[]) {
    const fixture = TestBed.createComponent(KanbanBoard);
    fixture.componentRef.setInput('leads', leads);
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
});
