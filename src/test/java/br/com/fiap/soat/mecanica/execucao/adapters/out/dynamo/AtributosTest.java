package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AtributosTest {

    @Test
    void dataTemLarguraFixaEMantemAOrdemPorTexto() {
        String inteiro = Atributos.data(Instant.parse("2026-11-06T10:00:00Z"));
        String meio = Atributos.data(Instant.parse("2026-11-06T10:00:00.500Z"));

        assertThat(inteiro).isEqualTo("2026-11-06T10:00:00.000Z");
        assertThat(inteiro).isLessThan(meio);
    }

    @Test
    void montaAtributosDeTextoENumero() {
        assertThat(Atributos.s("a").s()).isEqualTo("a");
        assertThat(Atributos.n(42).n()).isEqualTo("42");
    }
}
