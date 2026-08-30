import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MapaBusca } from './mapa-busca';
import { PontoMapa } from './mapa.model';

const leaflet = vi.hoisted(() => {
  type EventHandler = (event?: unknown) => void;

  let mapClickHandler: EventHandler | undefined;
  let markerDragHandler: EventHandler | undefined;
  let markerPosition = { lat: -25.4284, lng: -49.2733 };

  const mapInstance = {
    getCenter: vi.fn(() => ({ lat: -25.4284, lng: -49.2733 })),
    off: vi.fn((eventName: string, handler: EventHandler) => {
      if (eventName === 'click' && mapClickHandler === handler) {
        mapClickHandler = undefined;
      }
      return mapInstance;
    }),
    on: vi.fn((eventName: string, handler: EventHandler) => {
      if (eventName === 'click') {
        mapClickHandler = handler;
      }
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
    setLatLng: vi.fn(() => circleInstance),
    setRadius: vi.fn(() => circleInstance),
  };

  const tileLayerInstance = {
    addTo: vi.fn(() => tileLayerInstance),
  };

  return {
    circle: vi.fn(() => circleInstance),
    circleInstance,
    icon: vi.fn((options: unknown) => options),
    map: vi.fn(() => mapInstance),
    mapInstance,
    marker: vi.fn(() => markerInstance),
    markerInstance,
    reset(): void {
      vi.clearAllMocks();
      mapClickHandler = undefined;
      markerDragHandler = undefined;
      markerPosition = { lat: -25.4284, lng: -49.2733 };
    },
    tileLayer: vi.fn(() => tileLayerInstance),
    tileLayerInstance,
    triggerDrag(latitude: number, longitude: number): void {
      markerPosition = { lat: latitude, lng: longitude };
      markerDragHandler?.();
    },
    triggerMapClick(latitude: number, longitude: number): void {
      mapClickHandler?.({ latlng: { lat: latitude, lng: longitude } });
    },
  };
});

vi.mock('leaflet', () => ({
  circle: leaflet.circle,
  icon: leaflet.icon,
  map: leaflet.map,
  marker: leaflet.marker,
  tileLayer: leaflet.tileLayer,
}));

const INITIAL_POINT: PontoMapa = {
  latitude: -25.4284,
  longitude: -49.2733,
};

describe('MapaBusca', () => {
  beforeEach(() => {
    leaflet.reset();
    TestBed.configureTestingModule({ imports: [MapaBusca] });
  });

  async function createMapFixture() {
    const fixture = TestBed.createComponent(MapaBusca);
    fixture.componentRef.setInput('pontoCentral', INITIAL_POINT);
    fixture.componentRef.setInput('raioKm', 5);
    await fixture.whenStable();
    return fixture;
  }

  it('inicializa uma única instância com tiles atribuídos, marcador arrastável e círculo', async () => {
    await createMapFixture();

    expect(leaflet.map).toHaveBeenCalledTimes(1);
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

  it('permite confirmar por teclado o centro visível do mapa', async () => {
    const fixture = await createMapFixture();
    const emitted: PontoMapa[] = [];
    fixture.componentInstance.pontoCentralChange.subscribe((point) => emitted.push(point));
    leaflet.mapInstance.getCenter.mockReturnValueOnce({ lat: -25.46, lng: -49.31 });

    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    button.click();
    await fixture.whenStable();

    expect(emitted).toEqual([{ latitude: -25.46, longitude: -49.31 }]);
  });

  it('encerra listeners e remove a instância no destroy', async () => {
    const fixture = await createMapFixture();

    fixture.destroy();

    expect(leaflet.markerInstance.off).toHaveBeenCalledWith('dragend', expect.any(Function));
    expect(leaflet.mapInstance.off).toHaveBeenCalledWith('click', expect.any(Function));
    expect(leaflet.mapInstance.remove).toHaveBeenCalledOnce();
  });
});
