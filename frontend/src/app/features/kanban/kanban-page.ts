import { Component } from '@angular/core';

@Component({
  selector: 'app-kanban-page',
  template: `
    <section class="page-placeholder" aria-labelledby="kanban-page-title">
      <p class="page-placeholder__eyebrow">Acompanhamento comercial</p>
      <h1 id="kanban-page-title">Kanban</h1>
      <p class="page-placeholder__description">
        Organize as oportunidades por etapa e mantenha o próximo contato visível.
      </p>
      <div class="placeholder-state" role="status">
        <span class="placeholder-state__marker" aria-hidden="true">02</span>
        <div>
          <strong>Área preparada</strong>
          <p>As colunas e os cards de leads serão entregues nos sprints de consulta e Kanban.</p>
        </div>
      </div>
    </section>
  `
})
export class KanbanPage {}
