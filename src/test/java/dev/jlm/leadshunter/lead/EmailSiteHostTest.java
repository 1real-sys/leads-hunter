package dev.jlm.leadshunter.lead;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailSiteHostTest {
    @Test
    void normalizaWwwMasNaoAceitaDominioPaiDeSubdominio() {
        assertThat(EmailSiteHost.de("https://www.loja.exemplo.com.br/contato?x=1"))
            .isEqualTo("loja.exemplo.com.br");
        assertThat(EmailSiteHost.de("https://loja.exemplo.com.br/"))
            .isEqualTo("loja.exemplo.com.br");
        assertThat(EmailSiteHost.de("https://exemplo.com.br/"))
            .isNotEqualTo("loja.exemplo.com.br");
        assertThat(EmailSiteHost.de("file:///etc/passwd")).isNull();
    }
}
