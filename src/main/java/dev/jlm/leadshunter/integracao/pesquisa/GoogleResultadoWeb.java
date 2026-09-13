package dev.jlm.leadshunter.integracao.pesquisa;

import java.net.URI;

public record GoogleResultadoWeb(URI url, String titulo, String resumo) {
}
