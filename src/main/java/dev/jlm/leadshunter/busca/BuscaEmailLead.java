package dev.jlm.leadshunter.busca;

import dev.jlm.leadshunter.integracao.pesquisa.PesquisaLeadDados;

record BuscaEmailLead(Long id, boolean jaComEmail, String website, PesquisaLeadDados dados) { }
