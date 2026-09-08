import {
  afterRenderEffect,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { circle, geoJSON, icon, map as createMap, marker, tileLayer } from 'leaflet';
import type {
  Circle,
  GeoJSON,
  LatLng,
  Layer,
  LeafletMouseEvent,
  Map as LeafletMap,
  Marker,
  Path,
  PathOptions,
} from 'leaflet';
import { Subscription } from 'rxjs';
import { getApiErrorMessage } from '../../core/api/api-error-message';
import { GeografiaApi } from '../../core/api/geografia-api';
import {
  BboxGeografico,
  MunicipioGeoJsonProperties,
  MunicipiosGeoJsonResponse,
} from '../../shared/models/geografia.model';
import { classificarIdhm, formatarIdhm, LEGENDA_IDHM } from '../../shared/utils/idhm';
import { PontoMapa, pontoMapaValido, raioKmParaMetros } from './mapa.model';

const TILE_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const TILE_ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';
const INITIAL_ZOOM = 13;
const IDHM_PANE = 'idhm-municipios';
const IDHM_PANE_Z_INDEX = '350';
const VIEWPORT_THROTTLE_MS = 250;
const MAX_VIEWPORT_CACHE = 80;
const MAX_IDHM_VIEWPORT_AMPLITUDE = 5;
const IDHM_VIEWPORT_TOO_BROAD_MESSAGE =
  'Aproxime o mapa para visualizar a camada de IDHM nesta região.';

interface CelulaViewport {
  readonly chave: string;
  readonly bbox: BboxGeografico;
}

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

  protected readonly idhmAtivo = signal(false);
  protected readonly carregandoIdhm = signal(false);
  protected readonly mensagemErroIdhm = signal<string | null>(null);
  protected readonly mensagemOrientacaoIdhm = signal<string | null>(null);
  protected readonly totalMunicipiosIdhm = signal<number | null>(null);
  protected readonly legendaIdhm = LEGENDA_IDHM;

  private readonly destroyRef = inject(DestroyRef);
  private readonly geografiaApi = inject(GeografiaApi);
  private readonly mapContainer = viewChild.required<ElementRef<HTMLDivElement>>('mapContainer');

  private mapInstance: LeafletMap | null = null;
  private centerMarker: Marker | null = null;
  private radiusCircle: Circle | null = null;
  private idhmLayer: GeoJSON<MunicipioGeoJsonProperties> | null = null;
  private viewportTimer: ReturnType<typeof setTimeout> | null = null;
  private viewportRequest: Subscription | null = null;
  private readonly viewportCache = new Map<string, MunicipiosGeoJsonResponse>();
  private chavePendente: string | null = null;
  private chaveRenderizada: string | null = null;
  private requestSequence = 0;

  private readonly handleMapClick = (event: LeafletMouseEvent): void => {
    this.selectPoint(event.latlng);
  };

  private readonly handleMarkerDragEnd = (): void => {
    if (this.centerMarker) {
      this.selectPoint(this.centerMarker.getLatLng());
    }
  };

  private readonly handleMapMoveEnd = (): void => {
    if (this.idhmAtivo()) {
      this.agendarCarregamentoIdhm();
    }
  };

  constructor() {
    afterRenderEffect({
      mixedReadWrite: () => {
        const point = this.pontoCentral();
        const radiusKm = this.raioKm();

        if (!pontoMapaValido(point) || !Number.isFinite(radiusKm) || radiusKm <= 0) {
          return;
        }

        const radiusInMeters = raioKmParaMetros(radiusKm);

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

  protected alternarCamadaIdhm(): void {
    if (this.idhmAtivo()) {
      this.desativarCamadaIdhm();
      return;
    }

    this.idhmAtivo.set(true);
    this.mensagemErroIdhm.set(null);
    this.mensagemOrientacaoIdhm.set(null);
    this.agendarCarregamentoIdhm(0);
  }

  private initializeMap(point: PontoMapa, radiusInMeters: number): void {
    const center: [number, number] = [point.latitude, point.longitude];

    this.mapInstance = createMap(this.mapContainer().nativeElement, {
      attributionControl: true,
      keyboard: true,
      scrollWheelZoom: true,
      zoomControl: true,
    }).setView(center, INITIAL_ZOOM);

    const idhmPane = this.mapInstance.createPane(IDHM_PANE);
    idhmPane.style.zIndex = IDHM_PANE_Z_INDEX;

    tileLayer(TILE_URL, {
      attribution: TILE_ATTRIBUTION,
      maxZoom: 19,
    }).addTo(this.mapInstance);

    this.centerMarker = marker(center, {
      alt: 'Centro da área de busca',
      autoPan: true,
      draggable: true,
      icon: MARKER_ICON,
      keyboard: true,
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
    this.mapInstance.on('moveend', this.handleMapMoveEnd);
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

  private agendarCarregamentoIdhm(atraso = VIEWPORT_THROTTLE_MS): void {
    if (this.viewportTimer !== null) {
      clearTimeout(this.viewportTimer);
    }

    this.viewportTimer = setTimeout(() => {
      this.viewportTimer = null;
      this.carregarMunicipiosVisiveis();
    }, atraso);
  }

  private carregarMunicipiosVisiveis(): void {
    if (!this.mapInstance || !this.idhmAtivo()) {
      return;
    }

    const celula = this.obterCelulaViewport();
    if (!celula) {
      this.cancelarRequestPendente();
      this.totalMunicipiosIdhm.set(null);
      this.removerCamadaIdhm();
      this.mensagemErroIdhm.set(null);
      this.mensagemOrientacaoIdhm.set(IDHM_VIEWPORT_TOO_BROAD_MESSAGE);
      return;
    }

    this.mensagemOrientacaoIdhm.set(null);

    if (celula.chave === this.chavePendente) {
      return;
    }

    if (celula.chave === this.chaveRenderizada) {
      this.cancelarRequestPendente();
      return;
    }

    const respostaEmCache = this.viewportCache.get(celula.chave);
    if (respostaEmCache) {
      this.cancelarRequestPendente();
      this.renderizarMunicipios(respostaEmCache, celula.chave);
      return;
    }

    this.cancelarRequestPendente();
    this.chavePendente = celula.chave;
    this.carregandoIdhm.set(true);
    this.mensagemErroIdhm.set(null);
    this.mensagemOrientacaoIdhm.set(null);
    const requestId = ++this.requestSequence;

    this.viewportRequest = this.geografiaApi.listarMunicipios(celula.bbox).subscribe({
      next: (response) => {
        if (requestId !== this.requestSequence || !this.idhmAtivo()) {
          return;
        }

        this.adicionarAoCache(celula.chave, response);
        this.viewportRequest = null;
        this.chavePendente = null;
        this.carregandoIdhm.set(false);
        this.mensagemOrientacaoIdhm.set(null);
        this.renderizarMunicipios(response, celula.chave);
      },
      error: (error: unknown) => {
        if (requestId !== this.requestSequence || !this.idhmAtivo()) {
          return;
        }

        this.chavePendente = null;
        this.viewportRequest = null;
        this.carregandoIdhm.set(false);
        this.mensagemOrientacaoIdhm.set(null);
        this.totalMunicipiosIdhm.set(null);
        this.removerCamadaIdhm();
        this.mensagemErroIdhm.set(
          error instanceof HttpErrorResponse && error.status === 400
            ? 'Aproxime o mapa para carregar uma região menor.'
            : getApiErrorMessage(error),
        );
      },
    });
  }

  private obterCelulaViewport(): CelulaViewport | null {
    if (!this.mapInstance) {
      return null;
    }

    const bounds = this.mapInstance.getBounds();
    const oeste = Math.max(-180, bounds.getWest());
    const sul = Math.max(-90, bounds.getSouth());
    const leste = Math.min(180, bounds.getEast());
    const norte = Math.min(90, bounds.getNorth());

    if (![oeste, sul, leste, norte].every(Number.isFinite) || oeste >= leste || sul >= norte) {
      return null;
    }

    const maiorAmplitude = Math.max(leste - oeste, norte - sul);
    if (maiorAmplitude > MAX_IDHM_VIEWPORT_AMPLITUDE) {
      return null;
    }

    const tamanhoCelula = maiorAmplitude <= 0.25 ? 0.25 : maiorAmplitude <= 1 ? 1 : 5;
    const bbox: BboxGeografico = {
      minLng: this.normalizarCoordenada(Math.floor(oeste / tamanhoCelula) * tamanhoCelula),
      minLat: this.normalizarCoordenada(Math.floor(sul / tamanhoCelula) * tamanhoCelula),
      maxLng: this.normalizarCoordenada(Math.ceil(leste / tamanhoCelula) * tamanhoCelula),
      maxLat: this.normalizarCoordenada(Math.ceil(norte / tamanhoCelula) * tamanhoCelula),
    };

    if (
      bbox.maxLng - bbox.minLng > MAX_IDHM_VIEWPORT_AMPLITUDE ||
      bbox.maxLat - bbox.minLat > MAX_IDHM_VIEWPORT_AMPLITUDE
    ) {
      return null;
    }

    return {
      chave: [bbox.minLng, bbox.minLat, bbox.maxLng, bbox.maxLat].join(','),
      bbox,
    };
  }

  private normalizarCoordenada(valor: number): number {
    return Number(valor.toFixed(4));
  }

  private adicionarAoCache(chave: string, response: MunicipiosGeoJsonResponse): void {
    if (this.viewportCache.size >= MAX_VIEWPORT_CACHE) {
      const chaveMaisAntiga = this.viewportCache.keys().next().value;
      if (chaveMaisAntiga !== undefined) {
        this.viewportCache.delete(chaveMaisAntiga);
      }
    }

    this.viewportCache.set(chave, response);
  }

  private renderizarMunicipios(response: MunicipiosGeoJsonResponse, chave: string): void {
    if (!this.mapInstance || !this.idhmAtivo()) {
      return;
    }

    this.removerCamadaIdhm();
    this.idhmLayer = geoJSON<MunicipioGeoJsonProperties>(response, {
      bubblingMouseEvents: false,
      pane: IDHM_PANE,
      style: (feature) => this.estiloMunicipio(feature?.properties.idhm ?? null),
      onEachFeature: (feature, layer) => this.configurarMunicipio(feature.properties, layer),
    }).addTo(this.mapInstance);
    this.idhmLayer.eachLayer((layer) => this.configurarAcessibilidadeMunicipio(layer));
    this.radiusCircle?.bringToFront();
    this.chaveRenderizada = chave;
    this.totalMunicipiosIdhm.set(response.features.length);
  }

  private estiloMunicipio(idhm: number | null, destaque = false): PathOptions {
    return {
      pane: IDHM_PANE,
      color: destaque ? '#17332f' : '#ffffff',
      fillColor: classificarIdhm(idhm).cor,
      fillOpacity: destaque ? 0.78 : 0.58,
      opacity: 0.9,
      weight: destaque ? 2 : 0.8,
    };
  }

  private configurarMunicipio(properties: MunicipioGeoJsonProperties, layer: Layer): void {
    const path = layer as Path;
    const popup = this.criarPopupMunicipio(properties);
    const destacar = (): Path => path.setStyle(this.estiloMunicipio(properties.idhm, true));
    const restaurar = (): Path => path.setStyle(this.estiloMunicipio(properties.idhm));

    layer.bindPopup(popup);
    layer.on('mouseover', destacar);
    layer.on('mouseout', restaurar);
  }

  private configurarAcessibilidadeMunicipio(layer: Layer): void {
    const path = layer as Path;
    const elemento = path.getElement();
    const feature = (
      layer as Path & {
        feature?: { properties?: MunicipioGeoJsonProperties };
      }
    ).feature;
    const properties = feature?.properties;

    if (!elemento || !properties) {
      return;
    }

    const valor = formatarIdhm(properties.idhm);
    elemento.setAttribute('tabindex', '0');
    elemento.setAttribute('role', 'button');
    elemento.setAttribute(
      'aria-label',
      `${properties.nome}, ${properties.uf}. IDHM ${valor ?? 'não disponível'}, faixa ${classificarIdhm(properties.idhm).rotulo}.`,
    );
    elemento.addEventListener('focus', () =>
      path.setStyle(this.estiloMunicipio(properties.idhm, true)),
    );
    elemento.addEventListener('blur', () => path.setStyle(this.estiloMunicipio(properties.idhm)));
    elemento.addEventListener('keydown', (event) => {
      const keyboardEvent = event as KeyboardEvent;
      if (keyboardEvent.key === 'Enter' || keyboardEvent.key === ' ') {
        event.preventDefault();
        layer.openPopup();
      }
    });
  }

  private criarPopupMunicipio(properties: MunicipioGeoJsonProperties): HTMLElement {
    const classificacao = classificarIdhm(properties.idhm);
    const popup = document.createElement('div');
    popup.className = 'mapa-busca__idhm-popup';

    const localidade = document.createElement('strong');
    localidade.textContent = `${properties.nome} / ${properties.uf}`;
    popup.append(localidade);

    const valor = document.createElement('span');
    valor.textContent = `IDHM ${formatarIdhm(properties.idhm) ?? 'não disponível'} · ${classificacao.rotulo}`;
    popup.append(valor);

    if (properties.idhmReferencia !== null) {
      const referencia = document.createElement('small');
      referencia.textContent = `Referência ${properties.idhmReferencia} · Atlas Brasil`;
      popup.append(referencia);
    }

    return popup;
  }

  private desativarCamadaIdhm(): void {
    this.idhmAtivo.set(false);
    this.cancelarRequestPendente();
    this.mensagemErroIdhm.set(null);
    this.mensagemOrientacaoIdhm.set(null);
    this.totalMunicipiosIdhm.set(null);

    if (this.viewportTimer !== null) {
      clearTimeout(this.viewportTimer);
      this.viewportTimer = null;
    }

    this.removerCamadaIdhm();
  }

  private cancelarRequestPendente(): void {
    this.requestSequence += 1;
    this.viewportRequest?.unsubscribe();
    this.viewportRequest = null;
    this.chavePendente = null;
    this.carregandoIdhm.set(false);
  }

  private removerCamadaIdhm(): void {
    this.idhmLayer?.remove();
    this.idhmLayer = null;
    this.chaveRenderizada = null;
  }

  private destroyMap(): void {
    this.desativarCamadaIdhm();
    this.centerMarker?.off('dragend', this.handleMarkerDragEnd);
    this.mapInstance?.off('click', this.handleMapClick);
    this.mapInstance?.off('moveend', this.handleMapMoveEnd);
    this.mapInstance?.remove();

    this.radiusCircle = null;
    this.centerMarker = null;
    this.mapInstance = null;
  }
}
