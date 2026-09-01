import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { BuscaRequest } from '../../shared/models/busca.model';
import { BuscaForm } from './busca-form';
import { criarBuscaFormInicial } from './busca-form.model';

describe('BuscaForm', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [BuscaForm] });
  });

  async function createFixture() {
    const fixture = TestBed.createComponent(BuscaForm);
    fixture.componentRef.setInput('modelo', criarBuscaFormInicial());
    await fixture.whenStable();
    return fixture;
  }

  it('apresenta o estado inicial e bloqueia a confirmação sem categoria', async () => {
    const fixture = await createFixture();
    const requests: BuscaRequest[] = [];
    fixture.componentInstance.buscaConfirmada.subscribe((request) => requests.push(request));
    const button = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    const address = fixture.nativeElement.querySelector('#endereco-base') as HTMLInputElement;
    const radius = fixture.nativeElement.querySelector('#raio-km') as HTMLInputElement;

    expect(address.value).toBe('');
    expect(address.labels?.[0]?.textContent).toContain('Endereço de referência');
    expect(radius.value).toBe('5');
    expect(radius.min).toBe('1');
    expect(radius.max).toBe('20');
    expect(button.disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Não move o mapa');

    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new SubmitEvent('submit', { bubbles: true, cancelable: true }));
    await fixture.whenStable();
    expect(requests).toEqual([]);
  });

  it('emite um request válido com os valores exatos das categorias selecionadas', async () => {
    const fixture = await createFixture();
    const requests: BuscaRequest[] = [];
    fixture.componentInstance.buscaConfirmada.subscribe((request) => requests.push(request));

    const bakery = fixture.nativeElement.querySelector(
      '[data-categoria="PADARIA"]',
    ) as HTMLInputElement;
    const pharmacy = fixture.nativeElement.querySelector(
      '[data-categoria="FARMACIA"]',
    ) as HTMLInputElement;
    bakery.click();
    pharmacy.click();
    await fixture.whenStable();

    const button = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    expect(button.disabled).toBe(false);

    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new SubmitEvent('submit', { bubbles: true, cancelable: true }));
    await fixture.whenStable();

    expect(requests).toEqual([
      {
        enderecoBase: '',
        latitude: -25.4284,
        longitude: -49.2733,
        raioKm: 5,
        categorias: ['PADARIA', 'FARMACIA'],
      },
    ]);
    expect(button.textContent).toContain('Buscar leads');
  });

  it('aceita os limites geográficos e de raio definidos pelo backend', async () => {
    const fixture = await createFixture();
    const component = fixture.componentInstance;

    component.modelo.update((modelo) => ({
      ...modelo,
      latitude: -90,
      longitude: -180,
      raioKm: 1,
      categorias: { ...modelo.categorias, MERCADO: true },
    }));
    await fixture.whenStable();

    const button = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    expect(button.disabled).toBe(false);

    component.modelo.update((modelo) => ({
      ...modelo,
      latitude: 90,
      longitude: 180,
      raioKm: 20,
    }));
    await fixture.whenStable();

    expect(button.disabled).toBe(false);
  });

  it('rejeita coordenadas fora dos limites, raio fracionário e endereço acima de 255 caracteres', async () => {
    const fixture = await createFixture();
    const component = fixture.componentInstance;
    const button = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;

    component.modelo.update((modelo) => ({
      ...modelo,
      enderecoBase: 'a'.repeat(256),
      latitude: 91,
      longitude: -181,
      raioKm: 1.5,
      categorias: { ...modelo.categorias, MERCADO: true },
    }));
    await fixture.whenStable();

    expect(button.disabled).toBe(true);
    expect(component['buscaForm'].enderecoBase().invalid()).toBe(true);
    expect(component['buscaForm'].latitude().invalid()).toBe(true);
    expect(component['buscaForm'].longitude().invalid()).toBe(true);
    expect(component['buscaForm'].raioKm().errors()).toEqual(
      expect.arrayContaining([expect.objectContaining({ kind: 'integer' })]),
    );
  });

  it.each([
    ['latitude abaixo do mínimo', { latitude: -91 }, 'latitude'],
    ['latitude acima do máximo', { latitude: 91 }, 'latitude'],
    ['longitude abaixo do mínimo', { longitude: -181 }, 'longitude'],
    ['longitude acima do máximo', { longitude: 181 }, 'longitude'],
    ['raio abaixo do mínimo', { raioKm: 0 }, 'raioKm'],
    ['raio acima do máximo', { raioKm: 21 }, 'raioKm'],
  ] as const)('rejeita %s', async (_cenario, alteracao, campo) => {
    const fixture = await createFixture();
    const component = fixture.componentInstance;

    component.modelo.update((modelo) => ({
      ...modelo,
      ...alteracao,
      categorias: { ...modelo.categorias, MERCADO: true },
    }));
    await fixture.whenStable();

    expect(component['buscaForm'][campo]().invalid()).toBe(true);
    expect(
      (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).disabled,
    ).toBe(true);
  });

  it('rejeita coordenadas não finitas e categorias novamente vazias', async () => {
    const fixture = await createFixture();
    const component = fixture.componentInstance;
    const market = fixture.nativeElement.querySelector(
      '[data-categoria="MERCADO"]',
    ) as HTMLInputElement;

    market.click();
    market.click();
    market.dispatchEvent(new Event('blur'));
    component.modelo.update((modelo) => ({
      ...modelo,
      latitude: Number.NaN,
      longitude: Number.NaN,
    }));
    await fixture.whenStable();

    const button = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    expect(component['buscaForm'].latitude().invalid()).toBe(true);
    expect(component['buscaForm'].longitude().invalid()).toBe(true);
    expect(component['buscaForm'].categorias().invalid()).toBe(true);
    expect(fixture.nativeElement.querySelector('#categorias-error')?.textContent).toContain(
      'Selecione ao menos uma categoria',
    );
  });

  it('mantém o botão bloqueado e anuncia o loading enquanto a busca está em andamento', async () => {
    const fixture = await createFixture();
    const market = fixture.nativeElement.querySelector(
      '[data-categoria="MERCADO"]',
    ) as HTMLInputElement;
    market.click();
    await fixture.whenStable();

    fixture.componentRef.setInput('executando', true);
    await fixture.whenStable();

    const button = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    expect(button.getAttribute('aria-busy')).toBe('true');
    expect(button.textContent).toContain('Buscando leads');

    fixture.componentRef.setInput('executando', false);
    await fixture.whenStable();
    expect(button.disabled).toBe(false);
    expect(button.textContent).toContain('Buscar leads');
  });
});
