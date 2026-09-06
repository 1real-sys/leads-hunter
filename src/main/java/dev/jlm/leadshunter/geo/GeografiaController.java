package dev.jlm.leadshunter.geo;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/geografia")
@RequiredArgsConstructor
public class GeografiaController {

    private final MunicipioService municipioService;

    @GetMapping(value = "/municipios", produces = "application/geo+json")
    public ResponseEntity<MunicipiosGeoJsonResponse> listarMunicipios(
        @RequestParam String bbox
    ) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePublic())
            .body(municipioService.listarPorBbox(bbox));
    }
}
