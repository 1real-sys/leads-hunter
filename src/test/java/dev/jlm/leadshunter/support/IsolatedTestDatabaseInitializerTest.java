package dev.jlm.leadshunter.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IsolatedTestDatabaseInitializerTest {
    @Test
    void removeCatalogoDaAplicacaoPreservandoServidorEOpcoes() {
        assertThat(IsolatedTestDatabaseInitializer.serverUrl(
            "jdbc:mysql://localhost:3306/leadsradar?serverTimezone=America/Sao_Paulo&useSSL=false"))
            .isEqualTo("jdbc:mysql://localhost:3306/?serverTimezone=America/Sao_Paulo&useSSL=false");
        assertThat(IsolatedTestDatabaseInitializer.serverUrl("jdbc:mysql://127.0.0.1/aplicacao"))
            .isEqualTo("jdbc:mysql://127.0.0.1/");
    }

    @Test
    void recusaUrlsAmbiguasSemRecorrerAoBancoDaAplicacao() {
        for (String url : new String[] {
            "jdbc:h2:mem:test", "jdbc:mysql://usuario:senha@localhost/aplicacao",
            "jdbc:mysql://localhost/aplicacao#fragmento", "jdbc:mysql://host1,host2/aplicacao"
        }) {
            assertThatThrownBy(() -> IsolatedTestDatabaseInitializer.serverUrl(url))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
