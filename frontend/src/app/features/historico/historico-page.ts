import { Component } from '@angular/core';

@Component({
  selector: 'app-historico-page',
  template: `
    <section class="page-placeholder" aria-labelledby="historico-page-title">
      <p class="page-placeholder__eyebrow">Memória das buscas</p>
      <h1 id="historico-page-title">Histórico</h1>
      <p class="page-placeholder__description">
        Consulte as buscas anteriores e retome o contexto das oportunidades encontradas.
      </p>
      <div class="placeholder-state" role="status">
        <span class="placeholder-state__marker" aria-hidden="true">03</span>
        <div>
          <strong>Área preparada</strong>
          <p>A lista e o detalhe das buscas anteriores serão conectados à API em sprints próprios.</p>
        </div>
      </div>
    </section>
  `
})
export class HistoricoPage {}
