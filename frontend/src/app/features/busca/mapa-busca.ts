import {
  afterRenderEffect,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import { circle, icon, map as createMap, marker, tileLayer } from 'leaflet';
import type { Circle, LatLng, LeafletMouseEvent, Map as LeafletMap, Marker } from 'leaflet';
import { PontoMapa, pontoMapaValido, raioKmParaMetros } from './mapa.model';

const TILE_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const TILE_ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';
const INITIAL_ZOOM = 13;

const MARKER_ICON = icon({
  iconRetinaUrl: 'assets/leaflet/marker-icon-2x.png',
  iconUrl: 'assets/leaflet/marker-icon.png',
  shadowUrl: 'assets/leaflet/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
});

@Component({
  selector: 'app-mapa-busca',
  styleUrl: './mapa-busca.scss',
  templateUrl: './mapa-busca.html',
})
export class MapaBusca {
  readonly pontoCentral = input.required<PontoMapa>();
  readonly raioKm = input.required<number>();
  readonly pontoCentralChange = output<PontoMapa>();

  private readonly destroyRef = inject(DestroyRef);
  private readonly mapContainer = viewChild.required<ElementRef<HTMLDivElement>>('mapContainer');

  private mapInstance: LeafletMap | null = null;
  private centerMarker: Marker | null = null;
  private radiusCircle: Circle | null = null;

  private readonly handleMapClick = (event: LeafletMouseEvent): void => {
    this.selectPoint(event.latlng);
  };

  private readonly handleMarkerDragEnd = (): void => {
    if (this.centerMarker) {
      this.selectPoint(this.centerMarker.getLatLng());
    }
  };

  constructor() {
    afterRenderEffect({
      mixedReadWrite: () => {
        const point = this.pontoCentral();
        const radiusInMeters = raioKmParaMetros(this.raioKm());

        if (!pontoMapaValido(point)) {
          throw new RangeError('O ponto central do mapa possui coordenadas inválidas.');
        }

        if (!this.mapInstance) {
          this.initializeMap(point, radiusInMeters);
          return;
        }

        this.synchronizeLayers(point, radiusInMeters);
      },
    });

    this.destroyRef.onDestroy(() => this.destroyMap());
  }

  protected useVisibleCenter(): void {
    if (this.mapInstance) {
      this.selectPoint(this.mapInstance.getCenter());
    }
  }

  private initializeMap(point: PontoMapa, radiusInMeters: number): void {
    const center: [number, number] = [point.latitude, point.longitude];

    this.mapInstance = createMap(this.mapContainer().nativeElement, {
      attributionControl: true,
      keyboard: true,
      scrollWheelZoom: true,
      zoomControl: true,
    }).setView(center, INITIAL_ZOOM);

    tileLayer(TILE_URL, {
      attribution: TILE_ATTRIBUTION,
      maxZoom: 19,
    }).addTo(this.mapInstance);

    this.centerMarker = marker(center, {
      alt: 'Centro da área de busca',
      autoPan: true,
      draggable: true,
      icon: MARKER_ICON,
      title: 'Arraste para alterar o centro da busca',
    }).addTo(this.mapInstance);

    this.radiusCircle = circle(center, {
      className: 'mapa-busca__radius',
      color: '#176b61',
      fillColor: '#176b61',
      fillOpacity: 0.12,
      radius: radiusInMeters,
      weight: 2,
    }).addTo(this.mapInstance);

    this.mapInstance.on('click', this.handleMapClick);
    this.centerMarker.on('dragend', this.handleMarkerDragEnd);
  }

  private synchronizeLayers(point: PontoMapa, radiusInMeters: number): void {
    const center: [number, number] = [point.latitude, point.longitude];

    this.centerMarker?.setLatLng(center);
    this.radiusCircle?.setLatLng(center);
    this.radiusCircle?.setRadius(radiusInMeters);
  }

  private selectPoint(position: LatLng): void {
    const point: PontoMapa = {
      latitude: Number(position.lat.toFixed(6)),
      longitude: Number(position.lng.toFixed(6)),
    };

    if (!pontoMapaValido(point)) {
      return;
    }

    this.centerMarker?.setLatLng(position);
    this.radiusCircle?.setLatLng(position);
    this.pontoCentralChange.emit(point);
  }

  private destroyMap(): void {
    this.centerMarker?.off('dragend', this.handleMarkerDragEnd);
    this.mapInstance?.off('click', this.handleMapClick);
    this.mapInstance?.remove();

    this.radiusCircle = null;
    this.centerMarker = null;
    this.mapInstance = null;
  }
}
