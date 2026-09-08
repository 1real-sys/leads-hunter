import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_ROUTES } from '../../core/api/api-routes';
import { ApiErrorResponse } from '../../shared/models/api-error-response.model';
import { NomeBloqueadoResponse } from '../../shared/models/bloqueio.model';
import { BloqueiosPage } from './bloqueios-page';

const BLOQUEIO: NomeBloqueadoResponse = {
  id: 7,
  termo: 'Supermercados BH',
  criadoEm: '2026-09-08T10:30:00',
};

describe('BloqueiosPage', () => {
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BloqueiosPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function renderizar() {
    const fixture = TestBed.createComponent(BloqueiosPage);
    await fixture.whenStable();
    return fixture;
  }

  async function preencherTermo(fixture: Awaited<ReturnType<typeof renderizar>>, termo: string) {
    const input = fixture.nativeElement.querySelector('#termo-bloqueado') as HTMLInputElement;
    input.value = termo;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await fixture.whenStable();
  }

  it('carrega e apresenta os bloqueios na ordem devolvida pela API', async () => {
    const fixture = await renderizar();
    expect(fixture.nativeElement.textContent).toContain('Carregando bloqueios');

    const request = httpTesting.expectOne(API_ROUTES.bloqueios);
    expect(request.request.method).toBe('GET');
    request.flush([
      BLOQUEIO,
      { id: 8, termo: 'Extrabom', criadoEm: '2026-09-08T11:00:00' },
    ] satisfies NomeBloqueadoResponse[]);
    await fixture.whenStable();

    const itens = fixture.nativeElement.querySelectorAll('.bloqueios-page__list li');
    expect(itens).toHaveLength(2);
    expect(itens[0].textContent).toContain('Supermercados BH');
    expect(itens[1].textContent).toContain('Extrabom');
    expect(fixture.nativeElement.textContent).toContain('2 termos cadastrados');
  });

  it('representa a lista vazia sem sugerir que a chamada externa será evitada', async () => {
    const fixture = await renderizar();
    httpTesting.expectOne(API_ROUTES.bloqueios).flush([]);
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Nenhum termo cadastrado');
    expect(fixture.nativeElement.textContent).toContain('As buscas seguem normalmente');
    expect(fixture.nativeElement.querySelector('.bloqueios-page__list')).toBeNull();
  });

  it('valida o mínimo no formulário e cadastra um termo atualizando a lista', async () => {
    const fixture = await renderizar();
    httpTesting.expectOne(API_ROUTES.bloqueios).flush([]);
    await fixture.whenStable();

    await preencherTermo(fixture, 'BH');
    const submit = fixture.nativeElement.querySelector(
      '.bloqueios-page__submit',
    ) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Use pelo menos 3 caracteres.');

    await preencherTermo(fixture, '  Supermercados BH  ');
    expect(submit.disabled).toBe(false);
    submit.click();

    const request = httpTesting.expectOne(API_ROUTES.bloqueios);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ termo: 'Supermercados BH' });
    request.flush(BLOQUEIO, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('“Supermercados BH” foi adicionado');
    expect(fixture.nativeElement.querySelectorAll('.bloqueios-page__list li')).toHaveLength(1);
    expect(
      (fixture.nativeElement.querySelector('#termo-bloqueado') as HTMLInputElement).value,
    ).toBe('');
  });

  it('remove um termo e volta ao estado vazio', async () => {
    const fixture = await renderizar();
    httpTesting.expectOne(API_ROUTES.bloqueios).flush([BLOQUEIO]);
    await fixture.whenStable();

    const remover = fixture.nativeElement.querySelector(
      '[aria-label="Remover bloqueio Supermercados BH"]',
    ) as HTMLButtonElement;
    remover.click();

    const request = httpTesting.expectOne(API_ROUTES.bloqueio(7));
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('“Supermercados BH” foi removido');
    expect(fixture.nativeElement.textContent).toContain('Nenhum termo cadastrado');
  });

  it('exibe mensagem segura da API e permite repetir a listagem', async () => {
    const fixture = await renderizar();
    httpTesting.expectOne(API_ROUTES.bloqueios).flush(
      {
        timestamp: '2026-09-08T12:00:00Z',
        status: 500,
        codigo: 'ERRO_INTERNO',
        mensagem: 'Não foi possível consultar os bloqueios.',
        path: API_ROUTES.bloqueios,
      } satisfies ApiErrorResponse,
      { status: 500, statusText: 'Internal Server Error' },
    );
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Não foi possível consultar os bloqueios.');
    const repetir = [...fixture.nativeElement.querySelectorAll('button')].find(
      (button: HTMLButtonElement) => button.textContent?.trim() === 'Tentar novamente',
    ) as HTMLButtonElement;
    repetir.click();
    httpTesting.expectOne(API_ROUTES.bloqueios).flush([BLOQUEIO]);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelectorAll('.bloqueios-page__list li')).toHaveLength(1);
  });

  it('preserva a lista e mostra erro de negócio ao cadastrar duplicado', async () => {
    const fixture = await renderizar();
    httpTesting.expectOne(API_ROUTES.bloqueios).flush([BLOQUEIO]);
    await fixture.whenStable();
    await preencherTermo(fixture, 'supermercados bh');

    (fixture.nativeElement.querySelector('.bloqueios-page__submit') as HTMLButtonElement).click();
    httpTesting.expectOne(API_ROUTES.bloqueios).flush(
      {
        timestamp: '2026-09-08T12:00:00Z',
        status: 400,
        codigo: 'TERMO_BLOQUEADO_DUPLICADO',
        mensagem: 'Já existe um bloqueio cadastrado para esse termo.',
        path: API_ROUTES.bloqueios,
      } satisfies ApiErrorResponse,
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain(
      'Já existe um bloqueio cadastrado para esse termo.',
    );
    expect(fixture.nativeElement.querySelectorAll('.bloqueios-page__list li')).toHaveLength(1);
  });
});
