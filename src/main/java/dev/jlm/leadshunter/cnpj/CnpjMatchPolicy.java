package dev.jlm.leadshunter.cnpj;

import dev.jlm.leadshunter.lead.Lead;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Fail-closed policy gate for the exact-address path. */
@Component
public class CnpjMatchPolicy {

    private static final String HABILITADO = "cnpj.match.endereco-exato.habilitado";
    private static final String HABILITADO_ENV = "CNPJ_MATCH_ENDERECO_EXATO_HABILITADO";
    private static final String MUNICIPIOS = "cnpj.match.municipios-permitidos";
    private static final String MUNICIPIOS_ENV = "CNPJ_MATCH_MUNICIPIOS_PERMITIDOS";
    private static final String UFS = "cnpj.match.ufs-permitidas";
    private static final String UFS_ENV = "CNPJ_MATCH_UFS_PERMITIDAS";

    private final boolean habilitado;
    private final String municipiosConfigurados;
    private final String ufsConfiguradas;
    private final Set<String> municipiosPermitidos;
    private final Set<String> ufsPermitidas;

    @Autowired
    public CnpjMatchPolicy(Environment environment) {
        this(
            Boolean.parseBoolean(primeiro(environment, HABILITADO, HABILITADO_ENV)),
            primeiro(environment, MUNICIPIOS, MUNICIPIOS_ENV),
            primeiro(environment, UFS, UFS_ENV)
        );
    }

    public CnpjMatchPolicy(boolean habilitado, String municipios, String ufs) {
        this.habilitado = habilitado;
        this.municipiosConfigurados = municipios;
        this.ufsConfiguradas = ufs;
        this.municipiosPermitidos = parsearMunicipios(municipios);
        this.ufsPermitidas = parsearUfs(ufs);
    }

    public static CnpjMatchPolicy desabilitada() {
        return new CnpjMatchPolicy(false, null, null);
    }

    public static CnpjMatchPolicy habilitadaParaMunicipios(String... municipios) {
        return new CnpjMatchPolicy(true, String.join(",", municipios), null);
    }

    public boolean caminhoExatoHabilitado() {
        if (!habilitado) {
            return false;
        }
        return municipiosConfigurados != null
            ? !municipiosPermitidos.isEmpty()
            : !ufsPermitidas.isEmpty();
    }

    public boolean permite(Lead lead) {
        return lead != null && permite(lead.getMunicipioCodigoIbge(), lead.getUf());
    }

    public boolean permite(String municipioCodigoIbge, String uf) {
        if (!caminhoExatoHabilitado()) {
            return false;
        }
        if (municipiosConfigurados != null) {
            return municipiosPermitidos.contains(municipioCodigoIbge);
        }
        return uf != null && ufsPermitidas.contains(uf.trim().toUpperCase(Locale.ROOT));
    }

    public boolean habilitado() {
        return habilitado;
    }

    public Set<String> municipiosPermitidos() {
        return municipiosPermitidos;
    }

    public Set<String> ufsPermitidas() {
        return ufsPermitidas;
    }

    public String fingerprint() {
        String material = String.join(
            "|",
            Boolean.toString(habilitado),
            municipiosConfigurados == null ? "<null>" : municipiosConfigurados,
            ufsConfiguradas == null ? "<null>" : ufsConfiguradas
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    private static String primeiro(Environment environment, String principal, String alternativo) {
        String valor = environment.getProperty(principal);
        return valor != null ? valor : environment.getProperty(alternativo);
    }

    private static Set<String> parsearMunicipios(String valor) {
        return parsear(valor, "município", "\\d{7}");
    }

    private static Set<String> parsearUfs(String valor) {
        return parsear(valor, "UF", "[A-Za-z]{2}").stream()
            .map(item -> item.toUpperCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> parsear(String valor, String tipo, String regex) {
        if (valor == null || valor.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> valores = Arrays.stream(valor.split(","))
            .map(String::trim)
            .filter(item -> !item.isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String item : valores) {
            if (!item.matches(regex)) {
                throw new IllegalArgumentException(
                    "Valor de " + tipo + " não permitido na política CNPJ: " + item
                );
            }
        }
        return Collections.unmodifiableSet(valores);
    }
}
