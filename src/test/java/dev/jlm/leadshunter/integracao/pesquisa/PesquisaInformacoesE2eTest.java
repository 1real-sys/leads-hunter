package dev.jlm.leadshunter.integracao.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import dev.jlm.leadshunter.busca.*;
import dev.jlm.leadshunter.integracao.places.PlacesApiClient;
import dev.jlm.leadshunter.lead.*;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in: requer build Angular, MySQL e Chromium. Só a navegação externa é simulada. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"server.address=127.0.0.1", "pesquisa-inteligente.scraping.habilitado=true",
        "pesquisa-inteligente.brave.habilitado=false"})
@EnabledIfSystemProperty(named = "pesquisaE2e", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PesquisaInformacoesE2eTest {
    private static final String MANUAL = "Retornar amanhã. <script>não executar</script>";
    private static final LocalDateTime CONTATO = LocalDateTime.of(2026, 9, 1, 12, 30);
    private static final List<String> NOMES = List.of("Aurora", "Jasmim", "Lirio", "Vazio", "Bloqueio", "Completo");
    private static final String COMPLETO = MANUAL + "\n\n--- Pesquisa inteligente ---\nInstagram:\n"
        + "https://www.instagram.com/padariacompleto\n\nSite próprio:\nhttps://padariacompleto.example/"
        + "\n--- Fim da pesquisa inteligente ---";

    @Autowired private Environment environment;
    @Autowired private BuscaRepository buscas;
    @Autowired private LeadRepository leads;
    @Autowired private BuscaLeadRepository vinculos;
    @Autowired private BuscaInformacoesExecucaoService execucoes;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @MockitoBean private PlaywrightGooglePesquisaNavigator navigator;
    @MockitoBean private PlacesApiClient places;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Timeout(120)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void botaoPesquisaHtmlSimuladoPersisteResultadosERestauraAcompanhamento(boolean usarBrave) throws Exception {
        Path frontend = Path.of("frontend/dist/frontend/browser").toAbsolutePath().normalize();
        assertThat(frontend.resolve("index.html")).as("Execute npm run build em frontend antes do E2E").isRegularFile();
        var tx = new TransactionTemplate(transactionManager);
        List<Long> leadIds = new ArrayList<>();
        List<Long> buscaIds = new ArrayList<>();
        tx.executeWithoutResult(status -> prepararDados(leadIds, buscaIds));
        Long buscaId = buscaIds.getFirst();
        var liberar = new CountDownLatch(1);
        var chamadasGoogle = new AtomicInteger();
        when(navigator.navegar(any())).thenAnswer(invocacao -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            URI uri = invocacao.getArgument(0);
            FontePesquisaWeb fonte = FontePesquisaWeb.deDestino(uri);
            assertThat(uri.toString()).doesNotContain("key=", "places");
            if (chamadasGoogle.incrementAndGet() == 1 && !liberar.await(45, TimeUnit.SECONDS)) {
                throw new IllegalStateException("O navegador de teste não liberou o lote");
            }
            if (usarBrave && fonte != FontePesquisaWeb.BRAVE) throw new GooglePesquisaWebBloqueadaException();
            return paginaSimulada(uri);
        });
        String origem = "http://127.0.0.1:" + environment.getRequiredProperty("local.server.port");
        String rota = "/historico/" + buscaId;
        var posts = new AtomicInteger();
        var gets = new AtomicInteger();
        List<String> erros = new ArrayList<>();
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
             BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                 .setServiceWorkers(ServiceWorkerPolicy.BLOCK))) {
            // Serve apenas o build local; chamadas da API atravessam o servidor HTTP real.
            context.route("**/*", route -> servirFrontendOuApi(route, origem, frontend, posts, gets));
            Page page = context.newPage();
            page.setDefaultTimeout(15000);
            page.onPageError(erros::add);
            page.navigate(origem + rota);
            aguardarBotao(page);
            page.getByTestId("buscar-informacoes").press("Enter");
            page.waitForFunction("() => document.body.textContent.includes('Buscando informações…')");
            assertThat(page.getByTestId("buscar-informacoes").isDisabled()).isTrue();
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Voltar ao histórico").setExact(true)).click();
            page.waitForURL(origem + "/historico");
            int antes = gets.get();
            page.waitForTimeout(5500);
            assertThat(gets.get()).as("sem polling fora do detalhe").isEqualTo(antes);
            page.locator("a[href='" + rota + "']").click();
            page.waitForFunction("() => document.body.textContent.includes('0 de 6 leads')");
            liberar.countDown();
            page.waitForFunction("() => document.body.textContent.includes('Busca de informações concluída')");
            page.waitForFunction("() => document.querySelectorAll('.historico-detalhe__observacoes a').length === 6");
            validarPersistencia(buscaId, leadIds);
            assertThat(page.locator(".historico-detalhe__observacoes script").count()).isZero();
            for (Locator link : page.locator(".historico-detalhe__observacoes a").all()) {
                assertThat(link.getAttribute("target")).isEqualTo("_blank");
                assertThat(link.getAttribute("rel")).isEqualTo("noopener noreferrer");
            }
            // Retornar após a conclusão também deve restaurar o resultado persistido.
            page.reload();
            aguardarBotao(page);
            page.waitForFunction("() => document.body.textContent.includes('Busca de informações concluída')");
            assertThat(page.locator("tbody tr").count()).isEqualTo(6);
            assertThat(posts.get()).isOne();
            // 2 Aurora + 2 Jasmim + 3 Lirio + 3 Vazio + 4 fontes bloqueadas; Completo é ignorado.
            assertThat(chamadasGoogle.get()).isEqualTo(14);
            assertThat(erros).isEmpty();
            verifyNoInteractions(places);
        } finally {
            liberar.countDown();
            aguardarTermino(buscaId);
            // Remove exclusivamente os registros com IDs criados por este teste.
            tx.executeWithoutResult(status -> {
                entityManager.createQuery("delete from PesquisaInformacoesExecucao e where e.busca.id = :id")
                    .setParameter("id", buscaId).executeUpdate();
                for (Long id : buscaIds) {
                    vinculos.deleteAll(vinculos.findByBuscaIdOrderByScoreNaBuscaDesc(id));
                }
                vinculos.flush();
                buscas.deleteAllById(buscaIds);
                buscas.flush();
                leads.deleteAllById(leadIds);
            });
        }
    }

    private void prepararDados(List<Long> leadIds, List<Long> buscaIds) {
        Busca principal = novaBusca(6);
        Busca outra = novaBusca(1);
        buscaIds.add(principal.getId());
        buscaIds.add(outra.getId());
        for (int i = 0; i < 7; i++) {
            Lead lead = new Lead();
            lead.setGooglePlaceId("info-e2e-" + UUID.randomUUID());
            lead.setNome("Padaria " + (i < 6 ? NOMES.get(i) : "Exterior"));
            lead.setCategoria(CategoriaNegocio.PADARIA);
            lead.setMunicipioNome("Campinas");
            lead.setUf("SP");
            lead.setEnderecoFormatado("Rua das Flores, 10, Campinas SP");
            lead.setStatus(StatusFunil.CONTATADO);
            lead.setUltimoContatoEm(CONTATO);
            lead.setScore(80);
            lead.setTemperatura(Temperatura.QUENTE);
            lead.setObservacoes(i == 5 ? COMPLETO : MANUAL);
            lead = leads.save(lead);
            leadIds.add(lead.getId());
            BuscaLead vinculo = new BuscaLead();
            vinculo.setBusca(i < 6 ? principal : outra);
            vinculo.setLead(lead);
            vinculo.setScoreNaBusca(100 - i);
            vinculo.setTemperaturaNaBusca("QUENTE");
            vinculos.save(vinculo);
        }
    }

    private Busca novaBusca(int quantidade) {
        Busca busca = new Busca();
        busca.setEnderecoBase("INFO-01.7 — massa temporária");
        busca.setLatitude(new BigDecimal("-22.90"));
        busca.setLongitude(new BigDecimal("-47.06"));
        busca.setRaioKm(1);
        busca.setCategoriasBuscadas("PADARIA");
        busca.setTotalEncontrados(quantidade);
        return buscas.save(busca);
    }

    private GooglePesquisaPagina paginaSimulada(URI uri) throws IOException {
        String consulta = URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8);
        String nome = NOMES.stream().filter(consulta::contains).findFirst().orElseThrow();
        boolean instagram = !consulta.contains("site oficial");
        FontePesquisaWeb fonte = FontePesquisaWeb.deDestino(uri);
        boolean bloqueio = nome.equals("Bloqueio");
        boolean ausencia = nome.equals("Vazio") || nome.equals("Jasmim") && !instagram
            || nome.equals("Lirio") && instagram;
        String slug = "padaria" + nome.toLowerCase(java.util.Locale.ROOT);
        String url = instagram ? "https://www.instagram.com/" + slug : "https://" + slug + ".example/";
        String html = fonte == FontePesquisaWeb.BING
            ? paginaBing(nome, url, bloqueio, ausencia)
            : paginaGoogle(nome, url, bloqueio, ausencia);
        if (fonte == FontePesquisaWeb.BRAVE) {
            html = html.replace("id=\"search\"", "id=\"web\"").replace("class=\"g\"", "class=\"snippet\"")
                .replace("<a href=", "<a class=\"heading-serpresult\" href=")
                .replace("<div>Padaria", "<div class=\"snippet-description\">Padaria")
                .replace("Sua pesquisa não encontrou nenhum documento correspondente.", "No results found");
        }
        return new GooglePesquisaPagina(uri, 200, "Pesquisa simulada", html);
    }

    private String paginaGoogle(String nome, String url, boolean bloqueio, boolean ausencia) throws IOException {
        String arquivo = bloqueio ? "google-captcha.html"
            : ausencia ? "google-sem-resultados.html" : "google-e2e-resultado.html";
        String html;
        try (var stream = getClass().getResourceAsStream("/pesquisa/" + arquivo)) {
            html = new String(java.util.Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        }
        return html.replace("{{nome}}", "Padaria " + nome).replace("{{url}}", url);
    }

    private String paginaBing(String nome, String url, boolean bloqueio, boolean ausencia) {
        if (bloqueio) {
            return "<html><body><div id=\"captcha\"><p>Verify you are human</p></div></body></html>";
        }
        if (ausencia) {
            return "<html><body><ol id=\"b_results\"><li class=\"b_no\">There are no results for "
                + "Padaria " + nome + ".</li></ol></body></html>";
        }
        return "<html><body><ol id=\"b_results\"><li class=\"b_algo\"><h2><a href=\"" + url + "\">"
            + "Padaria " + nome + "</a></h2><div class=\"b_caption\"><p>Padaria em Campinas SP. "
            + "Rua das Flores, 10.</p></div></li></ol></body></html>";
    }

    private void servirFrontendOuApi(Route route, String origem, Path frontend, AtomicInteger posts, AtomicInteger gets) {
        URI uri = URI.create(route.request().url());
        if (!(uri.getScheme() + "://" + uri.getAuthority()).equals(origem)) {
            route.abort();
            return;
        }
        if (uri.getPath().startsWith("/api/")) {
            if (uri.getPath().endsWith("/informacoes")) {
                if (route.request().method().equals("POST")) posts.incrementAndGet();
                else gets.incrementAndGet();
            }
            route.resume();
            return;
        }
        Path arquivo = frontend.resolve(uri.getPath().substring(1)).normalize();
        if (!arquivo.startsWith(frontend)) {
            route.abort();
            return;
        }
        if (!Files.isRegularFile(arquivo)) arquivo = frontend.resolve("index.html");
        String nome = arquivo.getFileName().toString();
        String tipo = nome.endsWith(".js") ? "text/javascript" : nome.endsWith(".css") ? "text/css"
            : nome.endsWith(".html") ? "text/html" : "application/octet-stream";
        route.fulfill(new Route.FulfillOptions().setPath(arquivo).setContentType(tipo));
    }

    private void aguardarBotao(Page page) {
        page.waitForFunction("() => { const b = document.querySelector('[data-testid=buscar-informacoes]'); return b && !b.disabled; }");
    }

    private void validarPersistencia(Long buscaId, List<Long> ids) {
        var fim = execucoes.consultar(buscaId).orElseThrow();
        assertThat(fim.status()).isEqualTo(PesquisaInformacoesStatus.CONCLUIDA_COM_FALHAS);
        assertThat(fim.totalLeads()).isEqualTo(6);
        assertThat(fim.progresso()).isEqualTo(6);
        assertThat(fim.processados()).isEqualTo(4);
        assertThat(fim.ignoradosJaCompletos()).isOne();
        assertThat(fim.comInstagram()).isEqualTo(2);
        assertThat(fim.comSite()).isEqualTo(2);
        assertThat(fim.comAmbos()).isOne();
        assertThat(fim.semInformacoes()).isOne();
        assertThat(fim.falhas()).isOne();
        assertThat(fim.erroCodigo()).isEqualTo(PesquisaInformacoesErro.PESQUISA_INDISPONIVEL);
        List<String> resultados = ids.stream().map(id -> leads.findById(id).orElseThrow().getObservacoes()).toList();
        assertThat(resultados.get(0)).isEqualTo(bloco("Instagram:\nhttps://www.instagram.com/padariaaurora\n\nSite próprio:\nhttps://padariaaurora.example/"));
        assertThat(resultados.get(1)).isEqualTo(bloco("Instagram:\nhttps://www.instagram.com/padariajasmim"));
        assertThat(resultados.get(2)).isEqualTo(bloco("Site próprio:\nhttps://padarialirio.example/"));
        assertThat(resultados.get(3)).isEqualTo(bloco("pesquisa inteligente não encontrou mais informações"));
        assertThat(resultados.get(4)).isEqualTo(MANUAL);
        assertThat(resultados.get(5)).isEqualTo(COMPLETO);
        assertThat(resultados.get(6)).as("lead de outra busca não é pesquisado").isEqualTo(MANUAL);
        for (Long id : ids) {
            Lead lead = leads.findById(id).orElseThrow();
            assertThat(lead.getStatus()).isEqualTo(StatusFunil.CONTATADO);
            assertThat(lead.getUltimoContatoEm()).isEqualTo(CONTATO);
            assertThat(lead.getScore()).isEqualTo(80);
        }
        assertThat(vinculos.findByBuscaIdOrderByScoreNaBuscaDesc(buscaId))
            .extracting(BuscaLead::getScoreNaBusca).containsExactly(100, 99, 98, 97, 96, 95);
    }

    private String bloco(String conteudo) {
        return MANUAL + "\n\n--- Pesquisa inteligente ---\n" + conteudo + "\n--- Fim da pesquisa inteligente ---";
    }

    private void aguardarTermino(Long id) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (execucoes.consultar(id).map(e -> e.status().ativa()).orElse(false) && System.nanoTime() < limite) {
            Thread.sleep(25);
        }
        assertThat(execucoes.consultar(id).map(e -> e.status().ativa()).orElse(false)).isFalse();
    }
}
