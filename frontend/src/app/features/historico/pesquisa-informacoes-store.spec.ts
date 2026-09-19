import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_ROUTES } from '../../core/api/api-routes';
import { PesquisaInformacoesExecucaoResponse } from '../../shared/models/busca.model';
import { PesquisaInformacoesStore } from './pesquisa-informacoes-store';

export const EXECUCAO: PesquisaInformacoesExecucaoResponse = {
  id: 90,
  buscaId: 42,
  status: 'EM_ANDAMENTO',
  usarBrave: true,
  criadoEm: '2026-09-12T12:00:00',
  iniciadoEm: '2026-09-12T12:00:00',
  atualizadoEm: '2026-09-12T12:00:00',
  terminadoEm: null,
  totalLeads: 2,
  progresso: 0,
  processados: 0,
  ignoradosJaCompletos: 0,
  comInstagram: 0,
  comSite: 0,
  comAmbos: 0,
  semInformacoes: 0,
  falhas: 0,
  erroCodigo: null,
  erroMensagem: null,
};

describe('PesquisaInformacoesStore', () => {
  let store: PesquisaInformacoesStore;
  let http: HttpTestingController;
  const rota = API_ROUTES.buscaInformacoes(42);

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [PesquisaInformacoesStore, provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(PesquisaInformacoesStore);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    store.limpar();
    http.verify();
    vi.useRealTimers();
  });

  it('restaura uma execução, consulta sem sobreposição e para ao concluir', async () => {
    const terminou = vi.fn();
    store.finalizada.subscribe(terminou);
    store.acompanhar(42);
    expect(store.podeIniciar()).toBe(false);
    http.expectOne(rota).flush(EXECUCAO);
    expect(store.ativa()).toBe(true);
    await vi.advanceTimersByTimeAsync(5000);
    const consulta = http.expectOne(rota);
    await vi.advanceTimersByTimeAsync(5000);
    http.expectNone(rota);
    consulta.flush({ ...EXECUCAO, status: 'CONCLUIDA', progresso: 2 });
    expect(terminou).toHaveBeenCalledOnce();
    expect(store.podeIniciar()).toBe(true);
    await vi.advanceTimersByTimeAsync(20000);
    http.expectNone(rota);
  });

  it('não duplica POST e aceita retorno pendente', () => {
    store.acompanhar(42);
    http.expectOne(rota).flush(null, { status: 204, statusText: 'No Content' });
    store.iniciar();
    store.iniciar();
    const request = http.expectOne(rota);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ usarBrave: true });
    expect(store.iniciando()).toBe(true);
    request.flush({ ...EXECUCAO, status: 'PENDENTE' }, { status: 202, statusText: 'Accepted' });
    expect(store.ativa()).toBe(true);
    expect(store.podeIniciar()).toBe(false);
  });

  it('erro no polling preserva progresso e exige reconciliação antes de novo POST', async () => {
    store.acompanhar(42);
    http.expectOne(rota).flush({ ...EXECUCAO, progresso: 1 });
    await vi.advanceTimersByTimeAsync(5000);
    http.expectOne(rota).error(new ProgressEvent('error'));
    expect(store.execucao()?.progresso).toBe(1);
    expect(store.erro()).toContain('conectar');
    store.iniciar();
    await vi.advanceTimersByTimeAsync(20000);
    http.expectNone(rota);
    store.retomar();
    expect(http.expectOne(rota).request.method).toBe('GET');
  });

  it('falha ambígua no POST nunca dispara retry automático', () => {
    store.acompanhar(42);
    http.expectOne(rota).flush(null);
    store.iniciar();
    http.expectOne(rota).error(new ProgressEvent('error'));
    store.iniciar();
    http.expectNone(rota);
    store.retomar();
    http.expectOne(rota).flush(EXECUCAO);
    expect(store.ativa()).toBe(true);
  });

  it('troca de busca cancela a requisição anterior e limpa seus dados', async () => {
    store.acompanhar(42);
    const antiga = http.expectOne(rota);
    store.acompanhar(43);
    expect(antiga.cancelled).toBe(true);
    http.expectOne(API_ROUTES.buscaInformacoes(43)).flush(null);
    await vi.advanceTimersByTimeAsync(6000);
    http.expectNone(rota);
    expect(store.execucao()).toBeNull();
  });

  it('destruir a tela cancela o timer sem cancelar a execução no servidor', async () => {
    store.acompanhar(42);
    http.expectOne(rota).flush(EXECUCAO);
    TestBed.resetTestingModule();
    await vi.advanceTimersByTimeAsync(6000);
    http.expectNone(() => true);
  });
});
