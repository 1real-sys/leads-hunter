import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ArquivoDownloader } from './arquivo-downloader';

describe('ArquivoDownloader', () => {
  const click = vi.fn();
  const remove = vi.fn();
  const append = vi.fn();
  const createObjectURL = vi.fn(() => 'blob:arquivo-temporario');
  const revokeObjectURL = vi.fn();
  const link = { click, download: '', hidden: false, href: '', remove };
  const documentMock = {
    body: { append },
    createElement: vi.fn(() => link),
    defaultView: { URL: { createObjectURL, revokeObjectURL } },
  } as unknown as Document;

  beforeEach(() => {
    vi.clearAllMocks();
    link.download = '';
    link.hidden = false;
    link.href = '';
    TestBed.configureTestingModule({
      providers: [{ provide: DOCUMENT, useValue: documentMock }],
    });
  });

  it('aciona o download e sempre libera a URL temporária', () => {
    const arquivo = new Blob(['conteúdo'], { type: 'text/csv' });

    TestBed.inject(ArquivoDownloader).baixar(arquivo, 'leads.csv');

    expect(createObjectURL).toHaveBeenCalledWith(arquivo);
    expect(link.href).toBe('blob:arquivo-temporario');
    expect(link.download).toBe('leads.csv');
    expect(link.hidden).toBe(true);
    expect(append).toHaveBeenCalledWith(link);
    expect(click).toHaveBeenCalledOnce();
    expect(remove).toHaveBeenCalledOnce();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:arquivo-temporario');
  });

  it('remove o link e libera a URL mesmo quando o clique falha', () => {
    click.mockImplementationOnce(() => {
      throw new Error('Falha simulada.');
    });

    expect(() =>
      TestBed.inject(ArquivoDownloader).baixar(new Blob(['conteúdo']), 'leads.csv'),
    ).toThrow('Falha simulada.');
    expect(remove).toHaveBeenCalledOnce();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:arquivo-temporario');
  });
});
