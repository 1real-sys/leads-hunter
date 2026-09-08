const CNPJ_14_DIGITOS = /^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/;

export function formatarCnpj(valor: string | null | undefined): string | null {
  const cnpj = valor?.trim();
  if (!cnpj) {
    return null;
  }

  const partes = CNPJ_14_DIGITOS.exec(cnpj);
  return partes ? `${partes[1]}.${partes[2]}.${partes[3]}/${partes[4]}-${partes[5]}` : null;
}
