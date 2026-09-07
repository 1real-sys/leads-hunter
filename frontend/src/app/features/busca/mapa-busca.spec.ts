import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_ROUTES } from '../../core/api/api-routes';
import { MunicipiosGeoJsonResponse } from '../../shared/models/geografia.model';
import { MapaBusca } from './mapa-busca';
import { PontoMapa } from './mapa.model';

const leaflet = vi.hoisted(() => {
  type EventHandler = (event?: unknown) => void;
  type Bounds = { west: number; south: number; east: number; north: number };
  type Feature = {
    properties: {
      nome: string;
      uf: string;
      idhm: number | null;
      idhmReferencia: number | null;
    };
  };
  type FeatureLayer = {
    feature?: Feature;
    bindPopup: ReturnType<typeof vi.fn>;
    getElement: ReturnType<typeof vi.fn>;
    on: ReturnType<typeof vi.fn>;
    openPopup: ReturnType<typeof vi.fn>;
    setStyle: ReturnType<typeof vi.fn>;
  };
  type GeoJsonOptions = {
    pane?: string;
    style?: (feature?: Feature) => unknown;
    onEachFeature?: (feature: Feature, layer: FeatureLayer) => void;
  };

  const mapHandlers = new Map<string, EventHandler>();
  let markerDragHandler: EventHandler | undefined;
  let markerPosition = { lat: -25.4284, lng: -49.2733 };
  let bounds: Bounds = { west: -49.36, south: -25.5, east: -49.25, north: -25.35 };
  const pane = document.createElement('div');
  const featureLayers: FeatureLayer[] = [];

  const boundsInstance = {
    getEast: vi.fn(() => bounds.east),
    getNorth: vi.fn(() => bounds.north),
    getSouth: vi.fn(() => bounds.south),
    getWest: vi.fn(() => bounds.west),
  };

  const mapInstance = {
    createPane: vi.fn(() => pane),
    getBounds: vi.fn(() => boundsInstance),
    getCenter: vi.fn(() => ({ lat: -25.4284, lng: -49.2733 })),
    off: vi.fn((eventName: string, handler: EventHandler) => {
      if (mapHandlers.get(eventName) === handler) {
        mapHandlers.delete(eventName);
      }
      return mapInstance;
    }),
    on: vi.fn((eventName: string, handler: EventHandler) => {
      mapHandlers.set(eventName, handler);
      return mapInstance;
    }),
    remove: vi.fn(),
    setView: vi.fn(() => mapInstance),
  };

  const markerInstance = {
    addTo: vi.fn(() => markerInstance),
    getLatLng: vi.fn(() => markerPosition),
    off: vi.fn((eventName: string, handler: EventHandler) => {
      if (eventName === 'dragend' && markerDragHandler === handler) {
        markerDragHandler = undefined;
      }
      return markerInstance;
    }),
    on: vi.fn((eventName: string, handler: EventHandler) => {
      if (eventName === 'dragend') {
        markerDragHandler = handler;
      }
      return markerInstance;
    }),
    setLatLng: vi.fn((position: [number, number] | { lat: number; lng: number }) => {
      markerPosition = Array.isArray(position) ? { lat: position[0], lng: position[1] } : position;
      return markerInstance;
    }),
  };

  const circleInstance = {
    addTo: vi.fn(() => circleInstance),
    bringToFront: vi.fn(() => circleInstance),
    setLatLng: vi.fn(() => circleInstance),
    setRadius: vi.fn(() => circleInstance),
  };

  const tileLayerInstance = { addTo: vi.fn(() => tileLayerInstance) };
  const geoJsonLayer = {
    addTo: vi.fn(() => geoJsonLayer),
    eachLayer: vi.fn((callback: (layer: FeatureLayer) => void) => featureLayers.forEach(callback)),
    remove: vi.fn(() => geoJsonLayer),
  };

  const geoJSON = vi.fn((data: unknown, options?: GeoJsonOptions) => {
    featureLayers.length = 0;
    const features = (data as { features: Feature[] }).features;
    for (const feature of features) {
      const element = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      const layer: FeatureLayer = {
        feature,
        bindPopup: vi.fn(),
        getElement: vi.fn(() => element),
        on: vi.fn(),
        openPopup: vi.fn(),
        setStyle: vi.fn(() => layer),
      };
      options?.style?.(feature);
      options?.onEachFeature?.(feature, layer);
      featureLayers.push(layer);
    }
    return geoJsonLayer;
  });

  return {
    circle: vi.fn(() => circleInstance),
    circleInstance,
    featureLayers,
    geoJSON,
    geoJsonLayer,
    icon: vi.fn((options: unknown) => options),
    map: vi.fn(() => mapInstance),
    mapInstance,
    marker: vi.fn(() => markerInstance),
    markerInstance,
    pane,
    reset(): void {
      vi.clearAllMocks();
      mapHandlers.clear();
      markerDragHandler = undefined;
      markerPosition = { lat: -25.4284, lng: -49.2733 };
      bounds = { west: -49.36, south: -25.5, east: -49.25, north: -25.35 };
      pane.style.zIndex = '';
      featureLayers.length = 0;
    },
    setBounds(nextBounds: Bounds): void {
      bounds = nextBounds;
    },
    tileLayer: vi.fn(() => tileLayerInstance),
    triggerDrag(latitude: number, longitude: number): void {
      markerPosition = { lat: latitude, lng: longitude };
      markerDragHandler?.();
    },
    triggerMapClick(latitude: number, longitude: number): void {
      mapHandlers.get('click')?.({ latlng: { lat: latitude, lng: longitude } });
    },
    triggerMoveEnd(): void {
      mapHandlers.get('moveend')?.();
    },
  };
});

vi.mock('leaflet', () => ({
  circle: leaflet.circle,
  geoJSON: leaflet.geoJSON,
  icon: leaflet.icon,
  map: leaflet.map,
  marker: leaflet.marker,
  tileLayer: leaflet.tileLayer,
}));

const INITIAL_POINT: PontoMapa = { latitude: -25.4284, longitude: -49.2733 };
const MUNICIPIOS: MunicipiosGeoJsonResponse = {
  type: 'FeatureCollection',
  features: [
    {
      type: 'Feature',
      properties: {
        codigoIbge: '4106902',
        nome: 'Curitiba',
        uf: 'PR',
        idhm: 0.823,
        idhmReferencia: 2010,
      },
      geometry: {
        type: 'Polygon',
        coordinates: [
          [
            [-49.3, -25.5],
            [-49.2, -25.4],
            [-49.3, -25.5],
          ],
        ],
      },
    },
    {
      type: 'Feature',
      properties: {
        codigoIbge: '0000000',
        nome: 'Município sem índice',
        uf: 'PR',
        idhm: null,
        idhmReferencia: null,
      },
      geometry: {
        type: 'Polygon',
        coordinates: [
          [
            [-49.2, -25.4],
            [-49.1, -25.3],
            [-49.2, -25.4],
          ],
        ],
      },
    },
  ],
};

describe('MapaBusca', () => {
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    leaflet.reset();
    TestBed.configureTestingModule({
      imports: [MapaBusca],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  function aguardar(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  async function createMapFixture() {
    const fixture = TestBed.createComponent(MapaBusca);
    fixture.componentRef.setInput('pontoCentral', INITIAL_POINT);
    fixture.componentRef.setInput('raioKm', 5);
    await fixture.whenStable();
    return fixture;
  }

  async function enableIdhm() {
    const fixture = await createMapFixture();
    const toggle = fixture.nativeElement.querySelector(
      '.mapa-busca__idhm-toggle',
    ) as HTMLButtonElement;
    toggle.click();
    await aguardar(0);
    await fixture.whenStable();
    return { fixture, toggle };
  }

  function expectMunicipiosRequest(bbox: string) {
    const request = httpTesting.expectOne(
      (candidate) => candidate.url === API_ROUTES.geografiaMunicipios,
    );
    expect(request.request.params.get('bbox')).toBe(bbox);
    return request;
  }

  it('inicializa uma única instância com tiles, pane de IDHM, marcador e círculo', async () => {
    await createMapFixture();

    expect(leaflet.map).toHaveBeenCalledTimes(1);
    expect(leaflet.mapInstance.createPane).toHaveBeenCalledWith('idhm-municipios');
    expect(leaflet.pane.style.zIndex).toBe('350');
    expect(leaflet.tileLayer).toHaveBeenCalledWith(
      'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
      expect.objectContaining({
        attribution: expect.stringContaining('OpenStreetMap'),
        maxZoom: 19,
      }),
    );
    expect(leaflet.marker).toHaveBeenCalledWith(
      [INITIAL_POINT.latitude, INITIAL_POINT.longitude],
      expect.objectContaining({ draggable: true }),
    );
    expect(leaflet.circle).toHaveBeenCalledWith(
      [INITIAL_POINT.latitude, INITIAL_POINT.longitude],
      expect.objectContaining({ radius: 5_000 }),
    );
  });

  it('carrega e pinta a camada, exibe legenda e oferece popup acessível', async () => {
    const { fixture, toggle } = await enableIdhm();
    const request = expectMunicipiosRequest('-49.5,-25.5,-49.25,-25.25');
    request.flush(MUNICIPIOS);
    await fixture.whenStable();

    expect(toggle.getAttribute('aria-checked')).toBe('true');
    const legenda = fixture.nativeElement.querySelector('.mapa-busca__idhm-legenda') as HTMLElement;
    expect(legenda.textContent).toContain('Referência 2010 (Atlas Brasil)');
    expect(legenda.textContent).toContain('Sem IDHM');
    expect(leaflet.geoJSON).toHaveBeenCalledWith(
      MUNICIPIOS,
      expect.objectContaining({ pane: 'idhm-municipios' }),
    );
    expect(leaflet.circleInstance.bringToFront).toHaveBeenCalledOnce();

    const [curitiba, semIndice] = leaflet.featureLayers;
    const popup = curitiba.bindPopup.mock.calls[0][0] as HTMLElement;
    expect(popup.textContent).toContain('Curitiba / PR');
    expect(popup.textContent).toContain('IDHM 0,823 · Muito alto');
    expect(popup.textContent).toContain('Referência 2010 · Atlas Brasil');

    const elementoCuritiba = curitiba.getElement.mock.results[0].value as SVGElement;
    expect(elementoCuritiba.getAttribute('role')).toBe('button');
    expect(elementoCuritiba.getAttribute('tabindex')).toBe('0');
    expect(elementoCuritiba.getAttribute('aria-label')).toContain('IDHM 0,823, faixa Muito alto');
    elementoCuritiba.dispatchEvent(new FocusEvent('focus'));
    expect(curitiba.setStyle).toHaveBeenCalledWith(expect.objectContaining({ weight: 2 }));
    elementoCuritiba.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(curitiba.openPopup).toHaveBeenCalledOnce();

    const popupSemIndice = semIndice.bindPopup.mock.calls[0][0] as HTMLElement;
    expect(popupSemIndice.textContent).toContain('IDHM não disponível · Sem IDHM');
  });

  it('aplica throttle, evita chamadas duplicadas e reutiliza células em cache', async () => {
    const { fixture } = await enableIdhm();
    expectMunicipiosRequest('-49.5,-25.5,-49.25,-25.25').flush(MUNICIPIOS);
    await fixture.whenStable();

    leaflet.triggerMoveEnd();
    leaflet.triggerMoveEnd();
    await aguardar(275);
    httpTesting.expectNone(API_ROUTES.geografiaMunicipios);

    leaflet.setBounds({ west: -40.35, south: -20.35, east: -40.2, north: -20.2 });
    leaflet.triggerMoveEnd();
    leaflet.triggerMoveEnd();
    await aguardar(100);
    httpTesting.expectNone(API_ROUTES.geografiaMunicipios);
    await aguardar(175);
    expectMunicipiosRequest('-40.5,-20.5,-40,-20').flush(MUNICIPIOS);
    await fixture.whenStable();

    leaflet.setBounds({ west: -49.36, south: -25.5, east: -49.25, north: -25.35 });
    leaflet.triggerMoveEnd();
    await aguardar(275);

    httpTesting.expectNone(API_ROUTES.geografiaMunicipios);
    expect(leaflet.geoJSON).toHaveBeenCalledTimes(3);
  });

  it('cancela a resposta pendente quando o usuário retorna ao viewport já renderizado', async () => {
    const { fixture } = await enableIdhm();
    expectMunicipiosRequest('-49.5,-25.5,-49.25,-25.25').flush(MUNICIPIOS);
    await fixture.whenStable();

    leaflet.setBounds({ west: -40.35, south: -20.35, east: -40.2, north: -20.2 });
    leaflet.triggerMoveEnd();
    await aguardar(275);
    const requestObsoleta = expectMunicipiosRequest('-40.5,-20.5,-40,-20');

    leaflet.setBounds({ west: -49.36, south: -25.5, east: -49.25, north: -25.35 });
    leaflet.triggerMoveEnd();
    await aguardar(275);

    expect(requestObsoleta.cancelled).toBe(true);
    expect(leaflet.geoJSON).toHaveBeenCalledOnce();
  });

  it('remove a camada e interrompe novos carregamentos ao desligar o toggle', async () => {
    const { fixture, toggle } = await enableIdhm();
    expectMunicipiosRequest('-49.5,-25.5,-49.25,-25.25').flush(MUNICIPIOS);
    await fixture.whenStable();

    toggle.click();
    await fixture.whenStable();
    leaflet.triggerMoveEnd();
    await aguardar(25);

    expect(toggle.getAttribute('aria-checked')).toBe('false');
    expect(leaflet.geoJsonLayer.remove).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.querySelector('.mapa-busca__idhm-legenda')).toBeNull();
    httpTesting.expectNone(API_ROUTES.geografiaMunicipios);
  });

  it('orienta a aproximar o mapa quando a região excede o limite do backend', async () => {
    const { fixture } = await enableIdhm();
    expectMunicipiosRequest('-49.5,-25.5,-49.25,-25.25').flush(
      { codigo: 'REQUISICAO_INVALIDA', mensagem: 'Região ampla demais.' },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    const feedback = fixture.nativeElement.querySelector(
      '.mapa-busca__idhm-feedback--erro',
    ) as HTMLElement;
    expect(feedback.getAttribute('role')).toBe('alert');
    expect(feedback.textContent).toContain('Aproxime o mapa para carregar uma região menor.');
  });

  it('emite coordenadas válidas e atualiza as camadas ao clicar no mapa', async () => {
    const fixture = await createMapFixture();
    const emitted: PontoMapa[] = [];
    fixture.componentInstance.pontoCentralChange.subscribe((point) => emitted.push(point));

    leaflet.triggerMapClick(-25.4412344, -49.2819876);

    expect(emitted).toEqual([{ latitude: -25.441234, longitude: -49.281988 }]);
    expect(leaflet.markerInstance.setLatLng).toHaveBeenCalledWith({
      lat: -25.4412344,
      lng: -49.2819876,
    });
    expect(leaflet.circleInstance.setLatLng).toHaveBeenCalled();
  });

  it('emite coordenadas válidas ao terminar o arraste do marcador', async () => {
    const fixture = await createMapFixture();
    const emitted: PontoMapa[] = [];
    fixture.componentInstance.pontoCentralChange.subscribe((point) => emitted.push(point));

    leaflet.triggerDrag(-25.45, -49.3);

    expect(emitted).toEqual([{ latitude: -25.45, longitude: -49.3 }]);
  });

  it('atualiza o círculo quando o raio muda sem recriar o mapa', async () => {
    const fixture = await createMapFixture();
    fixture.componentRef.setInput('raioKm', 8);
    await fixture.whenStable();

    expect(leaflet.circleInstance.setRadius).toHaveBeenLastCalledWith(8_000);
    expect(leaflet.map).toHaveBeenCalledTimes(1);
  });

  it('mantém as últimas camadas válidas enquanto o formulário contém valor inválido', async () => {
    const fixture = await createMapFixture();
    leaflet.markerInstance.setLatLng.mockClear();
    leaflet.circleInstance.setLatLng.mockClear();
    leaflet.circleInstance.setRadius.mockClear();

    fixture.componentRef.setInput('pontoCentral', { latitude: 91, longitude: -49 });
    fixture.componentRef.setInput('raioKm', 0);
    await fixture.whenStable();

    expect(leaflet.markerInstance.setLatLng).not.toHaveBeenCalled();
    expect(leaflet.circleInstance.setLatLng).not.toHaveBeenCalled();
    expect(leaflet.circleInstance.setRadius).not.toHaveBeenCalled();
    expect(leaflet.map).toHaveBeenCalledTimes(1);
  });

  it('permite confirmar por teclado o centro visível do mapa', async () => {
    const fixture = await createMapFixture();
    const emitted: PontoMapa[] = [];
    fixture.componentInstance.pontoCentralChange.subscribe((point) => emitted.push(point));
    leaflet.mapInstance.getCenter.mockReturnValueOnce({ lat: -25.46, lng: -49.31 });

    const button = fixture.nativeElement.querySelector(
      '.mapa-busca__actions button:last-child',
    ) as HTMLButtonElement;
    button.click();
    await fixture.whenStable();

    expect(emitted).toEqual([{ latitude: -25.46, longitude: -49.31 }]);
  });

  it('encerra listeners e remove a instância e a camada no destroy', async () => {
    const { fixture } = await enableIdhm();
    expectMunicipiosRequest('-49.5,-25.5,-49.25,-25.25').flush(MUNICIPIOS);
    await fixture.whenStable();

    fixture.destroy();

    expect(leaflet.markerInstance.off).toHaveBeenCalledWith('dragend', expect.any(Function));
    expect(leaflet.mapInstance.off).toHaveBeenCalledWith('click', expect.any(Function));
    expect(leaflet.mapInstance.off).toHaveBeenCalledWith('moveend', expect.any(Function));
    expect(leaflet.geoJsonLayer.remove).toHaveBeenCalledOnce();
    expect(leaflet.mapInstance.remove).toHaveBeenCalledOnce();
  });
});
