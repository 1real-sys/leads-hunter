import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  imports: [RouterLink],
  selector: 'app-not-found-page',
  template: `
    <section class="page-placeholder" aria-labelledby="not-found-page-title">
      <p class="page-placeholder__eyebrow">Navegação</p>
      <h1 id="not-found-page-title">Página não encontrada</h1>
      <p class="page-placeholder__description">
        O endereço informado não corresponde a uma área disponível do Leads Hunter.
      </p>
      <a class="placeholder-action" routerLink="/busca">Voltar para Busca</a>
    </section>
  `
})
export class NotFoundPage {}
