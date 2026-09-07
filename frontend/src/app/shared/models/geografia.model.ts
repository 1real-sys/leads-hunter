import type { FeatureCollection, MultiPolygon, Polygon } from 'geojson';

export interface MunicipioGeoJsonProperties {
  readonly codigoIbge: string;
  readonly nome: string;
  readonly uf: string;
  readonly idhm: number | null;
  readonly idhmReferencia: number | null;
}

export type MunicipiosGeoJsonResponse = FeatureCollection<
  Polygon | MultiPolygon,
  MunicipioGeoJsonProperties
>;

export interface BboxGeografico {
  readonly minLng: number;
  readonly minLat: number;
  readonly maxLng: number;
  readonly maxLat: number;
}
