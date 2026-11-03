package br.com.fiap.soat.mecanica.execucao.adapters.out.messaging;

import br.com.fiap.soat.mecanica.execucao.support.IntegracaoTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class DlqDepthMetricsIT extends IntegracaoTest {

    @Autowired
    private DlqDepthMetrics metrics;

    @Autowired
    private MeterRegistry registry;

    @Test
    void gaugeRefleteAQuantidadeDeMensagensNaDlq() {
        for (int i = 0; i < 3; i++) {
            String corpo = "{\"n\":" + i + "}";
            SQS.sendMessage(r -> r.queueUrl(MENSAGERIA.dlq()).messageBody(corpo));
        }

        metrics.atualizar();

        assertThat(registry.get("dlq.depth").tag("fila", "execucao-teste-dlq").gauge().value()).isEqualTo(3.0);
    }
}
