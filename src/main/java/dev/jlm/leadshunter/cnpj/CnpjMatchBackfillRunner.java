package dev.jlm.leadshunter.cnpj;

import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Opt-in runner: report by default, application only with --aplicar. */
@Component
@ConditionalOnProperty(
    prefix = "leadhunter",
    name = "cnpj-match-runner",
    havingValue = "true"
)
@RequiredArgsConstructor
@Slf4j
public class CnpjMatchBackfillRunner implements ApplicationRunner {

    private final CnpjMatchDiagnosticoService diagnosticoService;
    private final CnpjMatchRelatorioWriter relatorioWriter;
    private final CnpjMatchAplicacaoService aplicacaoService;
    private final CnpjMatchPolicy policy;

    @Override
    public void run(ApplicationArguments args) {
        int limite = inteiro(args, "limite", CnpjMatchDiagnosticoService.LIMITE_PADRAO);
        Path relatorio = Path.of(opcao(args, "relatorio", "cnpj-match/relatorio.jsonl"));
        Path revisao = Path.of(opcao(args, "revisao", "cnpj-match/revisao.csv"));
        if (args.containsOption("aplicar")) {
            CnpjMatchAplicacaoService.ResultadoAplicacao resultado = aplicacaoService.aplicar(
                relatorio,
                revisao,
                limite
            );
            log.info(
                "Revisão CNPJ: total={}, certos={}, errados={}, inconclusivos={}, "
                    + "pendentes={}, cobertura={}",
                resultado.revisao().totalAprovacoes(),
                resultado.revisao().certos(),
                resultado.revisao().errados(),
                resultado.revisao().inconclusivos(),
                resultado.revisao().pendentes(),
                resultado.revisao().taxaCobertura()
            );
            resultado.itens().forEach(item -> log.info(
                "Aplicação CNPJ leadId={} cnpj={} status={}",
                item.leadId(), item.cnpj(), item.status()
            ));
            return;
        }

        var linhas = diagnosticoService.avaliarTodos(limite);
        CnpjMatchRelatorioWriter.RelatorioGerado resultado = relatorioWriter.escrever(
            relatorio,
            revisao,
            linhas,
            diagnosticoService.listarNumerosDescartados(),
            policy
        );
        log.info(
            "Relatório CNPJ gerado: {} linhas, JSONL={}, revisão={}, sha256={}",
            resultado.linhas(),
            resultado.caminhoJsonl(),
            resultado.caminhoRevisao(),
            resultado.hashSha256()
        );
    }

    private static String opcao(ApplicationArguments args, String nome, String padrao) {
        return args.getOptionValues(nome) == null
            ? padrao
            : args.getOptionValues(nome).getFirst();
    }

    private static int inteiro(ApplicationArguments args, String nome, int padrao) {
        return Integer.parseInt(opcao(args, nome, String.valueOf(padrao)));
    }
}
