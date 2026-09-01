import { Component, input, output } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { beforeEach, describe, expect, it } from 'vitest';
import { BuscaPage } from './busca-page';
import { MapaBusca } from './mapa-busca';
import { PontoMapa } from './mapa.model';

@Component({
  selector: 'app-mapa-busca',
  template: '',
})
class MapaBuscaStub {
  readonly pontoCentral = input.required<PontoMapa>();
  readonly raioKm = input.required<number>();
  readonly pontoCentralChange = output<PontoMapa>();
}

describe('BuscaPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [BuscaPage] });
    TestBed.overrideComponent(BuscaPage, {
      remove: { imports: [MapaBusca] },
      add: { imports: [MapaBuscaStub] },
    });
  });

  async function createFixture() {
    const fixture = TestBed.createComponent(BuscaPage);
    await fixture.whenStable();
    return fixture;
  }

  it('atualiza latitude e longitude do formulário quando o mapa emite um novo ponto', async () => {
    const fixture = await createFixture();
    const map = fixture.debugElement.query(By.directive(MapaBuscaStub))
      .componentInstance as MapaBuscaStub;

    map.pontoCentralChange.emit({ latitude: -20.3155, longitude: -40.3128 });
    await fixture.whenStable();

    const latitude = fixture.nativeElement.querySelector('#latitude') as HTMLInputElement;
    const longitude = fixture.nativeElement.querySelector('#longitude') as HTMLInputElement;
    expect(latitude.value).toBe('-20.3155');
    expect(longitude.value).toBe('-40.3128');
    expect(map.pontoCentral()).toEqual({ latitude: -20.3155, longitude: -40.3128 });
  });

  it('atualiza o ponto recebido pelo mapa quando as coordenadas do formulário mudam', async () => {
    const fixture = await createFixture();
    const map = fixture.debugElement.query(By.directive(MapaBuscaStub))
      .componentInstance as MapaBuscaStub;
    const latitude = fixture.nativeElement.querySelector('#latitude') as HTMLInputElement;
    const longitude = fixture.nativeElement.querySelector('#longitude') as HTMLInputElement;

    latitude.value = '-19.923456';
    latitude.dispatchEvent(new Event('input'));
    longitude.value = '-43.934567';
    longitude.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    expect(map.pontoCentral()).toEqual({ latitude: -19.923456, longitude: -43.934567 });
  });

  it('expõe o erro imediatamente quando uma coordenada editada fica inválida', async () => {
    const fixture = await createFixture();
    const latitude = fixture.nativeElement.querySelector('#latitude') as HTMLInputElement;

    latitude.value = '91';
    latitude.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    expect(latitude.getAttribute('aria-invalid')).toBe('true');
    expect(fixture.nativeElement.querySelector('#latitude-error')?.textContent).toContain(
      'latitude máxima é 90',
    );
  });

  it('sincroniza o slider de raio com o valor recebido pelo mapa', async () => {
    const fixture = await createFixture();
    const map = fixture.debugElement.query(By.directive(MapaBuscaStub))
      .componentInstance as MapaBuscaStub;
    const radius = fixture.nativeElement.querySelector('#raio-km') as HTMLInputElement;

    radius.value = '12';
    radius.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    expect(map.raioKm()).toBe(12);
    expect(fixture.nativeElement.querySelector('output')?.textContent).toContain('12 km');
  });
});
