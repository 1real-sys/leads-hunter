package dev.jlm.leadshunter.bloqueio;

import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NomeBloqueadoService {

    static final int TAMANHO_MINIMO_TERMO = 3;
    static final int TAMANHO_MAXIMO_TERMO = 120;

    private final NomeBloqueadoRepository repository;

    public List<NomeBloqueadoResponse> listar() {
        return repository.findAllByOrderByCriadoEmAscIdAsc().stream()
            .map(NomeBloqueadoResponse::from)
            .toList();
    }

    public List<String> listarTermosNormalizados() {
        return repository.findAllByOrderByCriadoEmAscIdAsc().stream()
            .map(NomeBloqueado::getTermoNormalizado)
            .toList();
    }

    public boolean estaBloqueado(String nome, Collection<String> termosNormalizados) {
        String nomeNormalizado = normalizar(nome);
        return !nomeNormalizado.isBlank()
            && termosNormalizados.stream().anyMatch(nomeNormalizado::contains);
    }

    @Transactional
    public NomeBloqueadoResponse cadastrar(String termo) {
        String termoExibido = validarELimpar(termo);
        String termoNormalizado = normalizar(termoExibido);

        if (repository.findByTermoNormalizado(termoNormalizado).isPresent()) {
            throw new NomeBloqueadoDuplicadoException();
        }

        try {
            return NomeBloqueadoResponse.from(
                repository.saveAndFlush(new NomeBloqueado(termoExibido, termoNormalizado))
            );
        } catch (DataIntegrityViolationException exception) {
            throw new NomeBloqueadoDuplicadoException(exception);
        }
    }

    @Transactional
    public void remover(Long id) {
        NomeBloqueado nomeBloqueado = repository.findById(id)
            .orElseThrow(() -> new NomeBloqueadoNaoEncontradoException(id));
        repository.delete(nomeBloqueado);
    }

    static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }

        return Normalizer.normalize(valor, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT)
            .trim();
    }

    private String validarELimpar(String termo) {
        if (termo == null || termo.isBlank()) {
            throw new NomeBloqueadoInvalidoException("Informe o termo a bloquear.");
        }

        String termoExibido = termo.trim();
        if (
            termoExibido.length() < TAMANHO_MINIMO_TERMO
                || termoExibido.length() > TAMANHO_MAXIMO_TERMO
        ) {
            throw new NomeBloqueadoInvalidoException(
                "O termo deve ter entre 3 e 120 caracteres."
            );
        }
        return termoExibido;
    }
}
