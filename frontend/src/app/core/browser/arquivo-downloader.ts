import { DOCUMENT } from '@angular/common';
import { inject, Service } from '@angular/core';

@Service()
export class ArquivoDownloader {
  private readonly document = inject(DOCUMENT);

  baixar(conteudo: Blob, nome: string): void {
    const urlApi = this.document.defaultView?.URL;
    if (urlApi === undefined) {
      throw new Error('API de download indisponível.');
    }

    const url = urlApi.createObjectURL(conteudo);
    const link = this.document.createElement('a');
    link.href = url;
    link.download = nome;
    link.hidden = true;

    try {
      this.document.body.append(link);
      link.click();
    } finally {
      link.remove();
      urlApi.revokeObjectURL(url);
    }
  }
}
