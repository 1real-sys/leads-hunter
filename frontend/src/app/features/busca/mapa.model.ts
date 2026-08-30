export interface PontoMapa {
  latitude: number;
  longitude: number;
}

export function pontoMapaValido(ponto: PontoMapa): boolean {
  return (
    Number.isFinite(ponto.latitude) &&
    ponto.latitude >= -90 &&
    ponto.latitude <= 90 &&
    Number.isFinite(ponto.longitude) &&
    ponto.longitude >= -180 &&
    ponto.longitude <= 180
  );
}

export function raioKmParaMetros(raioKm: number): number {
  if (!Number.isFinite(raioKm) || raioKm <= 0) {
    throw new RangeError('O raio do mapa deve ser um número maior que zero.');
  }

  return raioKm * 1_000;
}
