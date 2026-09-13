package dev.jlm.leadshunter.integracao.pesquisa;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PlaywrightGooglePesquisaNavigator implements GooglePesquisaWebNavigator, AutoCloseable {

    private static final String GOOGLE_SEARCH_HOST = "www.google.com";
    private static final String GOOGLE_SEARCH_PATH = "/search";
    private static final Set<String> RECURSOS_BLOQUEADOS = Set.of("image", "media", "font", "stylesheet");
    private static final Duration MARGEM_ESPERA_FUTURE = Duration.ofSeconds(5);

    private final long timeoutMs;
    private final long intervaloMinimoMs;
    private final int tamanhoMaximoRespostaBytes;
    private final ThreadPoolExecutor executor;

    private Playwright playwright;
    private Browser browser;
    private long ultimaNavegacaoEmNanos = Long.MIN_VALUE;

    @Autowired
    public PlaywrightGooglePesquisaNavigator(
        @Value("${pesquisa-inteligente.google.timeout-ms:15000}") long timeoutMs,
        @Value("${pesquisa-inteligente.google.max-resposta-bytes:2097152}") int tamanhoMaximoRespostaBytes,
        @Value("${pesquisa-inteligente.google.capacidade-fila:1}") int capacidadeFila,
        @Value("${pesquisa-inteligente.google.intervalo-minimo-ms:15000}") long intervaloMinimoMs
    ) {
        if (timeoutMs < 1_000
            || tamanhoMaximoRespostaBytes < 16_384
            || capacidadeFila < 1
            || intervaloMinimoMs < 0
            || intervaloMinimoMs > 60_000) {
            throw new IllegalArgumentException("Configuração inválida da pesquisa inteligente");
        }
        this.timeoutMs = timeoutMs;
        this.intervaloMinimoMs = intervaloMinimoMs;
        this.tamanhoMaximoRespostaBytes = tamanhoMaximoRespostaBytes;
        this.executor = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(capacidadeFila),
            tarefa -> {
                Thread thread = new Thread(tarefa, "pesquisa-inteligente-browser");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
        );
    }

    PlaywrightGooglePesquisaNavigator(long timeoutMs, int tamanhoMaximoRespostaBytes, int capacidadeFila) {
        this(timeoutMs, tamanhoMaximoRespostaBytes, capacidadeFila, 0);
    }

    @Override
    public GooglePesquisaPagina navegar(URI uri) {
        validarDestinoInicial(uri);

        Future<GooglePesquisaPagina> future;
        try {
            future = executor.submit(() -> navegarNaThreadDoBrowser(uri));
        } catch (RejectedExecutionException exception) {
            throw new GooglePesquisaWebOcupadaException();
        }

        try {
            return future.get(timeoutMs + MARGEM_ESPERA_FUTURE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException exception) {
            future.cancel(true);
            throw new GooglePesquisaWebTimeoutException(exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new GooglePesquisaWebIndisponivelException(exception);
        } catch (ExecutionException exception) {
            Throwable causa = exception.getCause();
            if (causa instanceof GooglePesquisaWebException pesquisaException) {
                throw pesquisaException;
            }
            throw new GooglePesquisaWebIndisponivelException(causa);
        }
    }

    private GooglePesquisaPagina navegarNaThreadDoBrowser(URI uri) {
        try {
            aguardarIntervaloMinimo();
            Browser navegador = obterBrowser();
            Browser.NewContextOptions opcoesContexto = new Browser.NewContextOptions()
                .setAcceptDownloads(false)
                .setJavaScriptEnabled(true)
                .setLocale("pt-BR")
                .setTimezoneId("America/Sao_Paulo")
                .setServiceWorkers(ServiceWorkerPolicy.BLOCK)
                .setViewportSize(1280, 900)
                .setExtraHTTPHeaders(Map.of(
                    "Accept-Language", "pt-BR,pt;q=0.9,en;q=0.7"
                ));

            try (BrowserContext contexto = navegador.newContext(opcoesContexto)) {
                Page pagina = contexto.newPage();
                pagina.setDefaultTimeout(timeoutMs);
                pagina.setDefaultNavigationTimeout(timeoutMs);
                pagina.route("**/*", rota -> {
                    String tipoRecurso = rota.request().resourceType();
                    if (RECURSOS_BLOQUEADOS.contains(tipoRecurso) || !destinoPermitido(rota.request().url())) {
                        rota.abort();
                    } else {
                        rota.resume();
                    }
                });

                Response resposta = pagina.navigate(
                    uri.toString(),
                    new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(timeoutMs)
                );
                aguardarConteudoUtil(pagina);

                URI urlFinal = URI.create(pagina.url());
                if (!hostGooglePermitido(urlFinal.getHost())) {
                    throw new GooglePesquisaWebIndisponivelException();
                }

                String html = pagina.content();
                if (html.getBytes(StandardCharsets.UTF_8).length > tamanhoMaximoRespostaBytes) {
                    throw new GooglePesquisaWebFormatoInvalidoException();
                }

                int status = resposta == null ? 200 : resposta.status();
                return new GooglePesquisaPagina(urlFinal, status, pagina.title(), html);
            }
        } catch (TimeoutError exception) {
            throw new GooglePesquisaWebTimeoutException(exception);
        } catch (GooglePesquisaWebException exception) {
            throw exception;
        } catch (PlaywrightException exception) {
            invalidarBrowserSeDesconectado();
            if (browserNaoInstalado(exception)) {
                throw new GooglePesquisaWebIndisponivelException(
                    "O Chromium headless da pesquisa inteligente não está instalado. "
                        + "Execute o instalador de browsers do Playwright."
                );
            }
            throw new GooglePesquisaWebIndisponivelException(exception);
        } catch (IllegalArgumentException exception) {
            throw new GooglePesquisaWebFormatoInvalidoException(exception);
        }
    }

    private void aguardarIntervaloMinimo() {
        if (intervaloMinimoMs == 0) {
            return;
        }
        long agora = System.nanoTime();
        long intervaloNanos = TimeUnit.MILLISECONDS.toNanos(intervaloMinimoMs);
        long esperaNanos = ultimaNavegacaoEmNanos == Long.MIN_VALUE
            ? 0
            : intervaloNanos - (agora - ultimaNavegacaoEmNanos);
        try {
            if (esperaNanos > 0) {
                TimeUnit.NANOSECONDS.sleep(esperaNanos);
            }
            ultimaNavegacaoEmNanos = System.nanoTime();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GooglePesquisaWebIndisponivelException(exception);
        }
    }

    private Browser obterBrowser() {
        if (browser != null && browser.isConnected()) {
            return browser;
        }
        fecharRecursosBrowser();
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
            new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(List.of(
                    "--disable-background-networking",
                    "--disable-component-update",
                    "--disable-dev-shm-usage",
                    "--no-first-run"
                ))
        );
        return browser;
    }

    private void aguardarConteudoUtil(Page pagina) {
        try {
            pagina.waitForSelector(
                "h3, #search, #captcha, form[action*=\"/sorry\"]",
                new Page.WaitForSelectorOptions().setTimeout(Math.min(timeoutMs, 4_000))
            );
        } catch (TimeoutError ignored) {
            // O parser diferencia uma ausência real de uma mudança inesperada no HTML.
        }
    }

    private void validarDestinoInicial(URI uri) {
        if (uri == null
            || !"https".equalsIgnoreCase(uri.getScheme())
            || !GOOGLE_SEARCH_HOST.equalsIgnoreCase(uri.getHost())
            || !GOOGLE_SEARCH_PATH.equals(uri.getPath())) {
            throw new IllegalArgumentException("Destino de pesquisa não permitido");
        }
    }

    private boolean destinoPermitido(String url) {
        try {
            URI uri = URI.create(url);
            String esquema = uri.getScheme();
            if ("data".equalsIgnoreCase(esquema) || "blob".equalsIgnoreCase(esquema)) {
                return true;
            }
            return "https".equalsIgnoreCase(esquema) && hostGooglePermitido(uri.getHost());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean hostGooglePermitido(String host) {
        if (host == null) {
            return false;
        }
        String normalizado = host.toLowerCase(Locale.ROOT);
        return normalizado.equals("google.com")
            || normalizado.endsWith(".google.com")
            || normalizado.equals("google.com.br")
            || normalizado.endsWith(".google.com.br")
            || normalizado.equals("gstatic.com")
            || normalizado.endsWith(".gstatic.com")
            || normalizado.equals("googleusercontent.com")
            || normalizado.endsWith(".googleusercontent.com")
            || normalizado.equals("googleapis.com")
            || normalizado.endsWith(".googleapis.com");
    }

    private boolean browserNaoInstalado(PlaywrightException exception) {
        String mensagem = exception.getMessage();
        return mensagem != null
            && (mensagem.contains("Executable doesn't exist") || mensagem.contains("Please run the following command"));
    }

    private void invalidarBrowserSeDesconectado() {
        if (browser != null && !browser.isConnected()) {
            fecharRecursosBrowser();
        }
    }

    private void fecharRecursosBrowser() {
        if (browser != null) {
            try {
                browser.close();
            } catch (RuntimeException ignored) {
                // O encerramento continua para liberar o driver local.
            } finally {
                browser = null;
            }
        }
        if (playwright != null) {
            try {
                playwright.close();
            } catch (RuntimeException ignored) {
                // O executor ainda será finalizado pelo ciclo de vida do componente.
            } finally {
                playwright = null;
            }
        }
    }

    @Override
    @PreDestroy
    public void close() {
        if (executor.isShutdown()) {
            return;
        }

        executor.getQueue().clear();
        try {
            Future<?> encerramento = executor.submit(this::fecharRecursosBrowser);
            encerramento.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | java.util.concurrent.TimeoutException | RejectedExecutionException ignored) {
            // A thread é daemon; o shutdownNow garante que não aceite novos trabalhos.
        } finally {
            executor.shutdownNow();
        }
    }
}
