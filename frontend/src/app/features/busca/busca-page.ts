import { Component } from '@angular/core';

@Component({
  selector: 'app-busca-page',
  template: `
    <section class="page-placeholder" aria-labelledby="busca-page-title">
      <p class="page-placeholder__eyebrow">Área principal</p>
      <h1 id="busca-page-title">Busca</h1>
      <p class="page-placeholder__description">
        Encontre estabelecimentos próximos e transforme contexto local em oportunidades comerciais.
      </p>
      <div class="placeholder-state" role="status">
        <span class="placeholder-state__marker" aria-hidden="true">01</span>
        <div>
          <strong>Estrutura pronta</strong>
          <p>O formulário e o mapa serão conectados nos próximos incrementos do MVP.</p>
        </div>
      </div>
    </section>
  `
})
export class BuscaPage {}
