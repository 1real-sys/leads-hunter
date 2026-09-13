package dev.jlm.leadshunter.integracao.pesquisa;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import com.microsoft.playwright.options.WaitUntilState;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Smoke opt-in: uma navegação por fonte candidata, sem retry, sem captcha e sem evasão. */
@EnabledIfSystemProperty(named = "pesquisaFontesLive", matches = "true")
class PesquisaFontesCandidatasLiveTest {

    private static final String CONSULTA = "Padaria Real Sorocaba SP";
    private static final Set<String> RECURSOS_BLOQUEADOS = Set.of("image", "media", "font", "stylesheet");

    private record Fonte(String nome, String url, String seletorResultado) {
    }

    private static final List<Fonte> FONTES = List.of(
        new Fonte("BING", "https://www.bing.com/search?q=%s&setlang=pt-BR&cc=BR", "li.b_algo"),
        new Fonte("MOJEEK", "https://www.mojeek.com/search?q=%s", "ul.results-standard")
    );

    @Test
    @Timeout(90)
    void deveDiagnosticarAcessoAsFontesCandidatas() {
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                 .setHeadless(true)
                 .setArgs(List.of("--disable-background-networking", "--disable-component-update",
                     "--disable-dev-shm-usage", "--no-first-run")))) {
            for (Fonte fonte : FONTES) {
                diagnosticar(browser, fonte);
            }
        }
    }

    private void diagnosticar(Browser browser, Fonte fonte) {
        try (BrowserContext contexto = browser.newContext(new Browser.NewContextOptions()
            .setLocale("pt-BR")
            .setTimezoneId("America/Sao_Paulo")
            .setServiceWorkers(ServiceWorkerPolicy.BLOCK)
            .setViewportSize(1280, 900)
            .setExtraHTTPHeaders(Map.of("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.7")))) {
            Page pagina = contexto.newPage();
            pagina.setDefaultNavigationTimeout(20_000);
            pagina.route("**/*", rota -> {
                if (RECURSOS_BLOQUEADOS.contains(rota.request().resourceType())) {
                    rota.abort();
                } else {
                    rota.resume();
                }
            });
            URI destino = URI.create(fonte.url().formatted(java.net.URLEncoder.encode(CONSULTA,
                java.nio.charset.StandardCharsets.UTF_8)));
            Response resposta = pagina.navigate(destino.toString(),
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(20_000));
            int status = resposta == null ? 0 : resposta.status();
            String texto = pagina.content().toLowerCase(Locale.ROOT);
            boolean bloqueada = status == 429 || status == 403 || contemBloqueio(texto);
            int resultados = pagina.querySelectorAll(fonte.seletorResultado()).size();
            System.out.println("FONTE=" + fonte.nome() + "; status=" + status
                + "; bloqueada=" + bloqueada + "; resultados=" + resultados
                + "; url_final=" + pagina.url().replaceAll("\\?.*$", ""));
        } catch (RuntimeException exception) {
            System.out.println("FONTE=" + fonte.nome() + "; erro=" + exception.getClass().getSimpleName()
                + "; bloqueada=indeterminado; resultados=0");
        }
    }

    private boolean contemBloqueio(String texto) {
        return texto.contains("unusual traffic")
            || texto.contains("verify you are human")
            || texto.contains("confirm you are a human")
            || texto.contains("are you a robot")
            || texto.contains("captcha")
            || texto.contains("access denied")
            || texto.contains("enable javascript")
            || texto.contains("too many requests");
    }
}
