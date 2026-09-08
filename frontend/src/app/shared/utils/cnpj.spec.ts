import { describe, expect, it } from 'vitest';
import { formatarCnpj } from './cnpj';

describe('CNPJ', () => {
  it('formata os 14 dígitos sem perder zeros à esquerda', () => {
    expect(formatarCnpj('01234567000189')).toBe('01.234.567/0001-89');
    expect(formatarCnpj(' 12345678000190 ')).toBe('12.345.678/0001-90');
  });

  it.each([null, undefined, '', '123', '12.345.678/0001-90', '1234567800019A'])(
    'trata %s como dado ausente ou inválido',
    (valor) => expect(formatarCnpj(valor)).toBeNull(),
  );
});
