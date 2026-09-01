import { Component, computed, model, output, signal } from '@angular/core';
import {
  form,
  FormField,
  max,
  maxLength,
  min,
  required,
  submit,
  validate,
} from '@angular/forms/signals';
import { BuscaRequest } from '../../shared/models/busca.model';
import {
  BuscaFormModel,
  criarBuscaRequest,
  listarCategoriasSelecionadas,
  OPCOES_CATEGORIA,
} from './busca-form.model';

@Component({
  imports: [FormField],
  selector: 'app-busca-form',
  styleUrl: './busca-form.scss',
  templateUrl: './busca-form.html',
})
export class BuscaForm {
  readonly modelo = model.required<BuscaFormModel>();
  readonly buscaConfirmada = output<BuscaRequest>();

  protected readonly opcoesCategoria = OPCOES_CATEGORIA;
  protected readonly buscaForm = form(this.modelo, (campos) => {
    maxLength(campos.enderecoBase, 255, {
      message: 'Use no máximo 255 caracteres.',
    });
    required(campos.latitude, { message: 'Informe a latitude.' });
    validate(campos.latitude, ({ value }) =>
      Number.isFinite(value())
        ? undefined
        : { kind: 'finite', message: 'Informe uma latitude válida.' },
    );
    min(campos.latitude, -90, { message: 'A latitude mínima é -90.' });
    max(campos.latitude, 90, { message: 'A latitude máxima é 90.' });
    required(campos.longitude, { message: 'Informe a longitude.' });
    validate(campos.longitude, ({ value }) =>
      Number.isFinite(value())
        ? undefined
        : { kind: 'finite', message: 'Informe uma longitude válida.' },
    );
    min(campos.longitude, -180, { message: 'A longitude mínima é -180.' });
    max(campos.longitude, 180, { message: 'A longitude máxima é 180.' });
    required(campos.raioKm, { message: 'Informe o raio.' });
    min(campos.raioKm, 1, { message: 'O raio mínimo é 1 km.' });
    max(campos.raioKm, 20, { message: 'O raio máximo é 20 km.' });
    validate(campos.raioKm, ({ value }) =>
      Number.isInteger(value())
        ? undefined
        : { kind: 'integer', message: 'O raio deve ser um número inteiro.' },
    );
    validate(campos.categorias, ({ value }) =>
      listarCategoriasSelecionadas(value()).length > 0
        ? undefined
        : { kind: 'required', message: 'Selecione ao menos uma categoria.' },
    );
  });

  private readonly assinaturaConfirmada = signal<string | null>(null);
  private readonly assinaturaAtual = computed(() => JSON.stringify(this.modelo()));
  protected readonly configuracaoConfirmada = computed(
    () => this.assinaturaConfirmada() === this.assinaturaAtual(),
  );

  protected confirmarConfiguracao(event: SubmitEvent): void {
    event.preventDefault();
    void submit(this.buscaForm, async () => {
      const request = criarBuscaRequest(this.modelo());
      this.assinaturaConfirmada.set(this.assinaturaAtual());
      this.buscaConfirmada.emit(request);
    });
  }
}
