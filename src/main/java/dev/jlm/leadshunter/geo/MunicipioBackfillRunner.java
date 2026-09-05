package dev.jlm.leadshunter.geo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "leadhunter",
    name = "backfill-municipio",
    havingValue = "true"
)
@RequiredArgsConstructor
@Slf4j
public class MunicipioBackfillRunner implements ApplicationRunner {

    private final MunicipioBackfillService backfillService;

    @Override
    public void run(ApplicationArguments args) {
        MunicipioBackfillService.Resultado resultado = backfillService.executar();
        log.info(
            "Backfill municipal concluído: {} leads analisados e {} atualizados.",
            resultado.analisados(),
            resultado.atualizados()
        );
    }
}
