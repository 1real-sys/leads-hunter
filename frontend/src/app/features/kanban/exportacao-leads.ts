import { Component, DestroyRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, Observable } from 'rxjs';
import { getApiErrorMessage } from '../../core/api/api-error-message';
import { ArquivoExportado, ExportacaoApi } from '../../core/api/exportacao-api';
import { FiltrosLead } from '../../core/api/lead-api';
import { ArquivoDownloader } from '../../core/browser/arquivo-downloader';

type FormatoExportacao = 'csv' | 'xlsx';

const MENSAGEM_FALHA_DOWNLOAD = 'Não foi possível iniciar o download. Tente novamente.';

@Component({
  selector: 'app-exportacao-leads',
  styleUrl: './exportacao-leads.scss',
  templateUrl: './exportacao-leads.html',
})
export class ExportacaoLeads {
  private readonly exportacaoApi = inject(ExportacaoApi);
  private readonly arquivoDownloader = inject(ArquivoDownloader);
  private readonly destroyRef = inject(DestroyRef);

  readonly filtros = input<FiltrosLead>({});

  protected readonly exportandoCsv = signal(false);
  protected readonly exportandoXlsx = signal(false);
  protected readonly erroCsv = signal<string | null>(null);
  protected readonly erroXlsx = signal<string | null>(null);
  protected readonly mensagemSucesso = signal<string | null>(null);

  protected exportarCsv(): void {
    this.exportar('csv');
  }

  protected exportarXlsx(): void {
    this.exportar('xlsx');
  }

  private exportar(formato: FormatoExportacao): void {
    const carregando = formato === 'csv' ? this.exportandoCsv : this.exportandoXlsx;
    const erro = formato === 'csv' ? this.erroCsv : this.erroXlsx;
    if (carregando()) {
      return;
    }

    carregando.set(true);
    erro.set(null);
    this.mensagemSucesso.set(null);

    const requisicao: Observable<ArquivoExportado> =
      formato === 'csv'
        ? this.exportacaoApi.exportarCsv(this.filtros())
        : this.exportacaoApi.exportarXlsx(this.filtros());

    requisicao
      .pipe(
        finalize(() => carregando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (arquivo) => {
          try {
            this.arquivoDownloader.baixar(arquivo.conteudo, arquivo.nome);
            this.mensagemSucesso.set(`Download de ${formato.toUpperCase()} iniciado.`);
          } catch {
            erro.set(MENSAGEM_FALHA_DOWNLOAD);
          }
        },
        error: (error: unknown) => erro.set(getApiErrorMessage(error)),
      });
  }
}
