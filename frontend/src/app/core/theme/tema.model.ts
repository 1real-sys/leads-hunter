export const TEMA_STORAGE_KEY = 'leads-hunter-theme';

export type TemaPreferido = 'system' | 'light' | 'dark';
export type TemaResolvido = Exclude<TemaPreferido, 'system'>;

export const TEMA_OPCOES: readonly { value: TemaPreferido; label: string }[] = [
  { value: 'system', label: 'Sistema' },
  { value: 'light', label: 'Claro' },
  { value: 'dark', label: 'Escuro' },
];

export function isTemaPreferido(value: unknown): value is TemaPreferido {
  return value === 'system' || value === 'light' || value === 'dark';
}
