package br.com.fiap.soat.mecanica.execucao.domain.ordem;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TempoMedioTest {

    @Test
    void mediaComUmaCasaDecimal() {
        assertThat(new TempoMedio(UUID.randomUUID(), "Oleo", 90, 2).minutosMedio()).isEqualByComparingTo("45.0");
    }

    @Test
    void arredondaMeioParaCima() {
        assertThat(new TempoMedio(UUID.randomUUID(), "Oleo", 10, 3).minutosMedio()).isEqualTo(new BigDecimal("3.3"));
        assertThat(new TempoMedio(UUID.randomUUID(), "Oleo", 5, 8).minutosMedio()).isEqualTo(new BigDecimal("0.6"));
    }

    @Test
    void semAmostrasEZero() {
        assertThat(new TempoMedio(UUID.randomUUID(), "Oleo", 0, 0).minutosMedio()).isEqualByComparingTo("0");
    }
}
