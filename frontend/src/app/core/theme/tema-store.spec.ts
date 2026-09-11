import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TEMA_STORAGE_KEY } from './tema.model';
import { TemaStore } from './tema-store';

type MediaQueryController = {
  alterar(matches: boolean): void;
  mediaQuery: MediaQueryList;
};

function instalarStorage(): Storage {
  const values = new Map<string, string>();
  const storage: Storage = {
    get length() {
      return values.size;
    },
    clear: vi.fn(() => values.clear()),
    getItem: vi.fn((key: string) => values.get(key) ?? null),
    key: vi.fn((index: number) => [...values.keys()][index] ?? null),
    removeItem: vi.fn((key: string) => values.delete(key)),
    setItem: vi.fn((key: string, value: string) => values.set(key, value)),
  };

  vi.stubGlobal('localStorage', storage);
  return storage;
}

function instalarMediaQuery(matchesInicial: boolean): MediaQueryController {
  let matches = matchesInicial;
  const listeners = new Set<(event: MediaQueryListEvent) => void>();

  const mediaQuery = {
    get matches() {
      return matches;
    },
    media: '(prefers-color-scheme: dark)',
    onchange: null,
    addEventListener: vi.fn((type: string, listener: EventListenerOrEventListenerObject) => {
      if (type === 'change' && typeof listener === 'function') {
        listeners.add(listener as (event: MediaQueryListEvent) => void);
      }
    }),
    removeEventListener: vi.fn((type: string, listener: EventListenerOrEventListenerObject) => {
      if (type === 'change' && typeof listener === 'function') {
        listeners.delete(listener as (event: MediaQueryListEvent) => void);
      }
    }),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  } as unknown as MediaQueryList;

  vi.stubGlobal(
    'matchMedia',
    vi.fn(() => mediaQuery),
  );

  return {
    mediaQuery,
    alterar(novoValor: boolean): void {
      matches = novoValor;
      const event = { matches: novoValor, media: mediaQuery.media } as MediaQueryListEvent;
      listeners.forEach((listener) => listener(event));
    },
  };
}

describe('TemaStore', () => {
  let storage: Storage;

  beforeEach(() => {
    TestBed.resetTestingModule();
    storage = instalarStorage();
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.style.colorScheme = '';
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.style.colorScheme = '';
  });

  it('usa o tema do sistema quando não existe preferência salva', () => {
    instalarMediaQuery(true);

    const store = TestBed.inject(TemaStore);

    expect(store.preferencia()).toBe('system');
    expect(store.temaResolvido()).toBe('dark');
    expect(document.documentElement.dataset['theme']).toBe('dark');
    expect(document.documentElement.style.colorScheme).toBe('dark');
  });

  it('restaura uma preferência explícita salva', () => {
    instalarMediaQuery(false);
    storage.setItem(TEMA_STORAGE_KEY, 'dark');

    const store = TestBed.inject(TemaStore);

    expect(store.preferencia()).toBe('dark');
    expect(store.temaResolvido()).toBe('dark');
  });

  it('ignora uma preferência salva desconhecida', () => {
    instalarMediaQuery(false);
    storage.setItem(TEMA_STORAGE_KEY, 'sepia');

    const store = TestBed.inject(TemaStore);

    expect(store.preferencia()).toBe('system');
    expect(store.temaResolvido()).toBe('light');
  });

  it('acompanha mudanças do sistema somente na preferência automática', () => {
    const media = instalarMediaQuery(false);
    const store = TestBed.inject(TemaStore);

    media.alterar(true);
    expect(store.temaResolvido()).toBe('dark');
    expect(document.documentElement.dataset['theme']).toBe('dark');

    store.definirPreferencia('light');
    media.alterar(false);
    media.alterar(true);

    expect(store.temaResolvido()).toBe('light');
    expect(document.documentElement.dataset['theme']).toBe('light');
  });

  it('persiste e aplica uma nova preferência', () => {
    instalarMediaQuery(false);
    const store = TestBed.inject(TemaStore);

    store.definirPreferencia('dark');

    expect(store.preferencia()).toBe('dark');
    expect(storage.getItem(TEMA_STORAGE_KEY)).toBe('dark');
    expect(document.documentElement.dataset['theme']).toBe('dark');
    expect(document.documentElement.style.colorScheme).toBe('dark');
  });

  it('continua funcional quando o storage não está disponível', () => {
    instalarMediaQuery(false);
    vi.spyOn(storage, 'getItem').mockImplementation(() => {
      throw new DOMException('Storage indisponível');
    });
    vi.spyOn(storage, 'setItem').mockImplementation(() => {
      throw new DOMException('Storage indisponível');
    });

    const store = TestBed.inject(TemaStore);
    store.definirPreferencia('dark');

    expect(store.preferencia()).toBe('dark');
    expect(document.documentElement.dataset['theme']).toBe('dark');
  });

  it('remove o listener do sistema ao destruir o injector', () => {
    const media = instalarMediaQuery(false);
    TestBed.inject(TemaStore);

    TestBed.resetTestingModule();

    expect(media.mediaQuery.removeEventListener).toHaveBeenCalledOnce();
  });
});
