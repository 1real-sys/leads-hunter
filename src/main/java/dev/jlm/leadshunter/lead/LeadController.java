package dev.jlm.leadshunter.lead;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/leads")
public class LeadController {

    @GetMapping
    public List<String> listar() {
        return Collections.emptyList();
    }
}
