import { DOCUMENT, DestroyRef, Service, computed, inject, signal } from '@angular/core';
import { TEMA_STORAGE_KEY, TemaPreferido, TemaResolvido, isTemaPreferido } from './tema.model';

const CONSULTA_TEMA_ESCURO = '(prefers-color-scheme: dark)';

@Service()
export class TemaStore {
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly mediaQuery = this.obterMediaQuery();
  private readonly sistemaEscuroState = signal(this.mediaQuery?.matches ?? false);
  private readonly preferenciaState = signal<TemaPreferido>(this.carregarPreferencia());

  readonly preferencia = this.preferenciaState.asReadonly();
  readonly temaResolvido = computed<TemaResolvido>(() => {
    const preferencia = this.preferenciaState();

    if (preferencia === 'system') {
      return this.sistemaEscuroState() ? 'dark' : 'light';
    }

    return preferencia;
  });

  constructor() {
    this.aplicarTema(this.temaResolvido());

    if (!this.mediaQuery) {
      return;
    }

    const aoAlterarTemaDoSistema = (event: MediaQueryListEvent): void => {
      this.sistemaEscuroState.set(event.matches);

      if (this.preferenciaState() === 'system') {
        this.aplicarTema(this.temaResolvido());
      }
    };

    this.mediaQuery.addEventListener('change', aoAlterarTemaDoSistema);
    this.destroyRef.onDestroy(() => {
      this.mediaQuery?.removeEventListener('change', aoAlterarTemaDoSistema);
    });
  }

  definirPreferencia(preferencia: TemaPreferido): void {
    this.preferenciaState.set(preferencia);
    this.salvarPreferencia(preferencia);
    this.aplicarTema(this.temaResolvido());
  }

  private carregarPreferencia(): TemaPreferido {
    try {
      const preferencia = this.document.defaultView?.localStorage.getItem(TEMA_STORAGE_KEY);
      return isTemaPreferido(preferencia) ? preferencia : 'system';
    } catch {
      return 'system';
    }
  }

  private salvarPreferencia(preferencia: TemaPreferido): void {
    try {
      this.document.defaultView?.localStorage.setItem(TEMA_STORAGE_KEY, preferencia);
    } catch {
      // O tema continua válido durante a sessão quando o storage não está disponível.
    }
  }

  private obterMediaQuery(): MediaQueryList | null {
    try {
      return this.document.defaultView?.matchMedia(CONSULTA_TEMA_ESCURO) ?? null;
    } catch {
      return null;
    }
  }

  private aplicarTema(tema: TemaResolvido): void {
    const root = this.document.documentElement;
    root.dataset['theme'] = tema;
    root.style.colorScheme = tema;
  }
}
