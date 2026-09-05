package dev.jlm.leadshunter.geo;

import dev.jlm.leadshunter.lead.Lead;
import dev.jlm.leadshunter.lead.LeadRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MunicipioBackfillService {

    private static final Pageable LOTE = PageRequest.of(0, 100);

    private final LeadRepository leadRepository;
    private final MunicipioService municipioService;

    public Resultado executar() {
        long ultimoId = 0;
        int analisados = 0;
        int atualizados = 0;

        while (true) {
            List<Lead> lote = leadRepository.buscarPendentesGeografiaAposId(ultimoId, LOTE);
            if (lote.isEmpty()) {
                break;
            }

            List<Lead> alterados = new ArrayList<>();
            for (Lead lead : lote) {
                ultimoId = lead.getId();
                analisados++;
                Optional<MunicipioInfo> municipio = municipioService.localizar(
                    lead.getLatitude(),
                    lead.getLongitude()
                );
                if (municipio.isPresent()) {
                    aplicar(lead, municipio.get());
                    alterados.add(lead);
                    atualizados++;
                }
            }
            if (!alterados.isEmpty()) {
                leadRepository.saveAll(alterados);
            }
        }
        return new Resultado(analisados, atualizados);
    }

    private void aplicar(Lead lead, MunicipioInfo municipio) {
        lead.setMunicipioCodigoIbge(municipio.codigoIbge());
        lead.setMunicipioNome(municipio.nome());
        lead.setUf(municipio.uf());
        lead.setIdhm(municipio.idhm());
        lead.setIdhmReferencia(municipio.idhmReferencia());
    }

    public record Resultado(int analisados, int atualizados) {
    }
}
