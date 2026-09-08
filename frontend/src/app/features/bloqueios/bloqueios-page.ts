import { DatePipe } from '@angular/common';
import {
  afterRenderEffect,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { form, FormField, maxLength, minLength, required, submit } from '@angular/forms/signals';
import { BloqueioApi } from '../../core/api/bloqueio-api';
import { getApiErrorMessage } from '../../core/api/api-error-message';
import { NomeBloqueadoResponse } from '../../shared/models/bloqueio.model';

type EstadoLista = 'loading' | 'success' | 'empty' | 'error';

interface BloqueioFormModel {
  termo: string;
}

@Component({
  imports: [DatePipe, FormField],
  selector: 'app-bloqueios-page',
  styleUrl: './bloqueios-page.scss',
  templateUrl: './bloqueios-page.html',
})
export class BloqueiosPage {
  private readonly bloqueioApi = inject(BloqueioApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly bloqueios = signal<NomeBloqueadoResponse[]>([]);
  protected readonly estadoLista = signal<EstadoLista>('loading');
  protected readonly mensagemErroLista = signal<string | null>(null);
  protected readonly mensagemOperacao = signal<string | null>(null);
  protected readonly mensagemErroOperacao = signal<string | null>(null);
  protected readonly cadastrando = signal(false);
  protected readonly removendoId = signal<number | null>(null);
  protected readonly modelo = signal<BloqueioFormModel>({ termo: '' });
  protected readonly bloqueioForm = form(this.modelo, (campos) => {
    required(campos.termo, { message: 'Informe um nome ou trecho.' });
    minLength(campos.termo, 3, { message: 'Use pelo menos 3 caracteres.' });
    maxLength(campos.termo, 120, { message: 'Use no máximo 120 caracteres.' });
  });

  private readonly feedbackLista = viewChild<ElementRef<HTMLElement>>('feedbackLista');
  private readonly feedbackOperacao = viewChild<ElementRef<HTMLElement>>('feedbackOperacao');

  constructor() {
    afterRenderEffect(() => {
      if (this.estadoLista() === 'error') {
        this.feedbackLista()?.nativeElement.focus();
      } else if (this.mensagemOperacao() || this.mensagemErroOperacao()) {
        this.feedbackOperacao()?.nativeElement.focus();
      }
    });

    this.carregar();
  }

  protected carregar(): void {
    this.estadoLista.set('loading');
    this.mensagemErroLista.set(null);

    this.bloqueioApi
      .listar()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (bloqueios) => {
          this.bloqueios.set(bloqueios);
          this.estadoLista.set(bloqueios.length === 0 ? 'empty' : 'success');
        },
        error: (error: unknown) => {
          this.mensagemErroLista.set(getApiErrorMessage(error));
          this.estadoLista.set('error');
        },
      });
  }

  protected cadastrar(event: SubmitEvent): void {
    event.preventDefault();
    if (this.estadoLista() === 'loading' || this.estadoLista() === 'error') {
      return;
    }
    this.limparFeedbackOperacao();

    void submit(this.bloqueioForm, async () => {
      const termo = this.modelo().termo.trim();
      this.cadastrando.set(true);

      this.bloqueioApi
        .cadastrar({ termo })
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (bloqueio) => {
            this.bloqueios.update((atuais) => [...atuais, bloqueio]);
            this.estadoLista.set('success');
            this.bloqueioForm().reset({ termo: '' });
            this.mensagemOperacao.set(`“${bloqueio.termo}” foi adicionado aos bloqueios.`);
            this.cadastrando.set(false);
          },
          error: (error: unknown) => {
            this.mensagemErroOperacao.set(getApiErrorMessage(error));
            this.cadastrando.set(false);
          },
        });
    });
  }

  protected remover(bloqueio: NomeBloqueadoResponse): void {
    if (this.removendoId() !== null) {
      return;
    }

    this.limparFeedbackOperacao();
    this.removendoId.set(bloqueio.id);

    this.bloqueioApi
      .remover(bloqueio.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.bloqueios.update((atuais) => atuais.filter((item) => item.id !== bloqueio.id));
          this.estadoLista.set(this.bloqueios().length === 0 ? 'empty' : 'success');
          this.mensagemOperacao.set(`“${bloqueio.termo}” foi removido dos bloqueios.`);
          this.removendoId.set(null);
        },
        error: (error: unknown) => {
          this.mensagemErroOperacao.set(getApiErrorMessage(error));
          this.removendoId.set(null);
        },
      });
  }

  private limparFeedbackOperacao(): void {
    this.mensagemOperacao.set(null);
    this.mensagemErroOperacao.set(null);
  }
}
