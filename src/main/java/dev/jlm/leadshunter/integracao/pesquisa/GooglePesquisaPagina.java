package dev.jlm.leadshunter.integracao.pesquisa;

import java.net.URI;

record GooglePesquisaPagina(URI urlFinal, int statusHttp, String titulo, String html) {
}
