package dev.jlm.leadshunter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jlm.leadshunter.geo.MunicipioBackfillRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class LeadsHunterApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertThat(applicationContext.getBeansOfType(MunicipioBackfillRunner.class)).isEmpty();
    }

}
