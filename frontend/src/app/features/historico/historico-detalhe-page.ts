import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

@Component({
  imports: [RouterLink],
  selector: 'app-historico-detalhe-page',
  template: `
    <section class="page-placeholder" aria-labelledby="historico-detalhe-title">
      <p class="page-placeholder__eyebrow">Memória das buscas</p>
      <h1 id="historico-detalhe-title">Busca #{{ buscaId }}</h1>
      <p class="page-placeholder__description">
        O detalhamento dos leads desta busca será disponibilizado na próxima etapa.
      </p>
      <a class="placeholder-action" routerLink="/historico">Voltar ao histórico</a>
    </section>
  `,
})
export class HistoricoDetalhePage {
  protected readonly buscaId = inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '';
}
