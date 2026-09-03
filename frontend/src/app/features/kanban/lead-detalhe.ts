import { CdkTrapFocus } from '@angular/cdk/a11y';
import { DatePipe } from '@angular/common';
import {
  afterNextRender,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { getApiErrorMessage } from '../../core/api/api-error-message';
import { LeadApi } from '../../core/api/lead-api';
import { AtualizarLeadRequest, LeadResponse } from '../../shared/models/lead.model';
import { ROTULOS_CATEGORIA, ROTULOS_TEMPERATURA, obterRotuloStatus } from './kanban.model';

function paraValorDatetimeLocal(iso: string | null): string {
  return (iso ?? '').slice(0, 16);
}

@Component({
  imports: [CdkTrapFocus, DatePipe],
  selector: 'app-lead-detalhe',
  styleUrl: './lead-detalhe.scss',
  templateUrl: './lead-detalhe.html',
})
export class LeadDetalhe {
  readonly lead = input.required<LeadResponse>();
  readonly fechado = output<void>();
  readonly leadAtualizado = output<LeadResponse>();

  private readonly leadApi = inject(LeadApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly botaoFechar = viewChild<ElementRef<HTMLButtonElement>>('botaoFechar');

  protected readonly tituloId = computed(() => `detalhe-lead-${this.lead().id}-titulo`);
  protected readonly rotuloStatus = computed(() => obterRotuloStatus(this.lead().status));
  protected readonly rotulosCategoria = ROTULOS_CATEGORIA;
  protected readonly rotulosTemperatura = ROTULOS_TEMPERATURA;

  protected readonly editando = signal(false);
  protected readonly salvando = signal(false);
  protected readonly observacoesDigitadas = signal('');
  protected readonly contatoDigitado = signal('');
  protected readonly mensagemErro = signal<string | null>(null);
  protected readonly salvouRecente = signal(false);
  protected readonly avisoAlteracoes = signal(false);

  private readonly observacoesOrigem = computed(() => this.lead().observacoes ?? '');
  private readonly contatoOrigem = computed(() => paraValorDatetimeLocal(this.lead().ultimoContatoEm));

  protected readonly observacoesAlteradas = computed(
    () => this.observacoesDigitadas() !== this.observacoesOrigem(),
  );
  protected readonly contatoAlterado = computed(() => {
    const novo = this.contatoDigitado();
    return novo !== '' && novo !== this.contatoOrigem();
  });
  protected readonly temAlteracaoSalvavel = computed(
    () => this.observacoesAlteradas() || this.contatoAlterado(),
  );
  protected readonly temAlteracaoPendente = computed(
    () => this.observacoesDigitadas() !== this.observacoesOrigem()
      || this.contatoDigitado() !== this.contatoOrigem(),
  );
  protected readonly camposDesabilitados = computed(() => this.salvando());

  constructor() {
    afterNextRender(() => this.botaoFechar()?.nativeElement.focus());
  }

  protected iniciarEdicao(): void {
    this.observacoesDigitadas.set(this.observacoesOrigem());
    this.contatoDigitado.set(this.contatoOrigem());
    this.mensagemErro.set(null);
    this.salvouRecente.set(false);
    this.avisoAlteracoes.set(false);
    this.editando.set(true);
  }

  protected cancelarEdicao(): void {
    this.editando.set(false);
    this.mensagemErro.set(null);
    this.avisoAlteracoes.set(false);
    this.observacoesDigitadas.set('');
    this.contatoDigitado.set('');
  }

  protected alterarObservacoes(evento: Event): void {
    this.observacoesDigitadas.set((evento.target as HTMLTextAreaElement).value);
    this.avisoAlteracoes.set(false);
  }

  protected alterarContato(evento: Event): void {
    this.contatoDigitado.set((evento.target as HTMLInputElement).value);
    this.avisoAlteracoes.set(false);
  }

  protected salvar(evento: SubmitEvent): void {
    evento.preventDefault();

    const alteracoes: { observacoes?: string; ultimoContatoEm?: string } = {};

    if (this.observacoesAlteradas()) {
      alteracoes.observacoes = this.observacoesDigitadas();
    }
    if (this.contatoAlterado()) {
      alteracoes.ultimoContatoEm = this.contatoDigitado();
    }

    if (Object.keys(alteracoes).length === 0 || this.salvando()) {
      return;
    }

    this.salvando.set(true);
    this.mensagemErro.set(null);
    this.avisoAlteracoes.set(false);

    this.leadApi
      .atualizar(this.lead().id, alteracoes as AtualizarLeadRequest)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (leadAtualizado) => {
          this.salvando.set(false);
          this.editando.set(false);
          this.salvouRecente.set(true);
          this.observacoesDigitadas.set('');
          this.contatoDigitado.set('');
          this.leadAtualizado.emit(leadAtualizado);
        },
        error: (error: unknown) => {
          this.salvando.set(false);
          this.mensagemErro.set(getApiErrorMessage(error));
        },
      });
  }

  protected fechar(): void {
    if (this.salvando()) {
      return;
    }
    if (this.editando() && this.temAlteracaoPendente()) {
      this.avisoAlteracoes.set(true);
      return;
    }
    this.fechado.emit();
  }
}
