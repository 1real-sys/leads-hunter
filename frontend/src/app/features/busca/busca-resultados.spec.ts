import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { BuscaResponse } from '../../shared/models/busca.model';
import { BuscaResultados } from './busca-resultados';

const RESULTADO: BuscaResponse = {
  id: 42,
  enderecoBase: 'Centro, Curitiba - PR',
  latitude: -25.4284,
  longitude: -49.2733,
  raioKm: 5,
  categorias: ['PADARIA', 'MERCADO'],
  totalEncontrados: 2,
  criadoEm: '2026-08-31T10:30:00',
  leads: [
    {
      id: 7,
      nome: 'Padaria Central',
      categoria: 'PADARIA',
      enderecoFormatado: 'Rua Central, 100',
      telefone: '(41) 3333-4444',
      whatsappUrl: 'https://wa.me/554133334444',
      score: 82,
      temperatura: 'QUENTE',
    },
    {
      id: 8,
      nome: 'Mercado Bairro',
      categoria: 'MERCADO',
      enderecoFormatado: 'Rua das Flores, 20',
      telefone: null,
      whatsappUrl: null,
      score: 55,
      temperatura: 'MORNO',
    },
  ],
};

describe('BuscaResultados', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BuscaResultados],
      providers: [provideRouter([])],
    });
  });

  async function renderizar(resultado: BuscaResponse) {
    const fixture = TestBed.createComponent(BuscaResultados);
    fixture.componentRef.setInput('resultado', resultado);
    await fixture.whenStable();
    return fixture;
  }

  it('apresenta o resumo, todos os leads e suas classificações em texto', async () => {
    const fixture = await renderizar(RESULTADO);
    const conteudo = fixture.nativeElement.textContent as string;
    const cards = fixture.nativeElement.querySelectorAll('.lead-resumo');
    const kanban = fixture.nativeElement.querySelector(
      'a[routerLink="/kanban"]',
    ) as HTMLAnchorElement;

    expect(conteudo).toContain('Centro, Curitiba - PR');
    expect(conteudo).toContain('5 km');
    expect(conteudo).toContain('31/08/2026 às 10:30');
    expect(conteudo).toContain('Padaria');
    expect(conteudo).toContain('Mercado');
    expect(cards).toHaveLength(2);
    expect(conteudo).toContain('Padaria Central');
    expect(conteudo).toContain('Rua Central, 100');
    expect(conteudo).toContain('(41) 3333-4444');
    expect(conteudo).toContain('82');
    expect(conteudo).toContain('Quente');
    expect(conteudo).toContain('Morno');
    expect(kanban.getAttribute('href')).toBe('/kanban');
  });

  it('exibe WhatsApp somente quando a URL vier do backend e protege a nova aba', async () => {
    const fixture = await renderizar(RESULTADO);
    const links = fixture.nativeElement.querySelectorAll(
      '.lead-resumo__whatsapp',
    ) as NodeListOf<HTMLAnchorElement>;

    expect(links).toHaveLength(1);
    expect(links[0].getAttribute('href')).toBe('https://wa.me/554133334444');
    expect(links[0].getAttribute('target')).toBe('_blank');
    expect(links[0].getAttribute('rel')).toBe('noopener noreferrer');
    expect(links[0].getAttribute('aria-label')).toContain('Padaria Central');
  });

  it('omite dados opcionais ausentes sem produzir textos ou links falsos', async () => {
    const fixture = await renderizar({
      ...RESULTADO,
      enderecoBase: null,
      categorias: ['OUTROS'],
      totalEncontrados: 1,
      leads: [
        {
          id: 99,
          nome: null,
          categoria: null,
          enderecoFormatado: null,
          telefone: null,
          whatsappUrl: null,
          score: null,
          temperatura: null,
        },
      ],
    });
    const conteudo = fixture.nativeElement.textContent as string;

    expect(conteudo).toContain('Lead #99');
    expect(conteudo).not.toContain('Endereço de referência');
    expect(conteudo).not.toContain('Telefone');
    expect(conteudo).not.toContain('Score');
    expect(fixture.nativeElement.querySelector('.lead-resumo__temperatura')).toBeNull();
    expect(fixture.nativeElement.querySelector('.lead-resumo__whatsapp')).toBeNull();
  });

  it('mantém a busca vazia concluída, orienta nova configuração e permite abrir o Kanban', async () => {
    const fixture = await renderizar({
      ...RESULTADO,
      totalEncontrados: 0,
      leads: [],
    });
    const conteudo = fixture.nativeElement.textContent as string;

    expect(conteudo).toContain('Busca #42 concluída');
    expect(conteudo).toContain('Nenhum lead encontrado');
    expect(conteudo).toContain('aumentar o raio');
    expect(fixture.nativeElement.querySelector('.resultados__lista')).toBeNull();
    expect(fixture.nativeElement.querySelector('a[routerLink="/kanban"]')).toBeTruthy();
  });
});
