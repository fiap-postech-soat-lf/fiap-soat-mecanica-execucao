package br.com.fiap.soat.mecanica.execucao.adapters.in.messaging;

import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.ProcessadorMensagem;
import br.com.fiap.soat.mecanica.execucao.support.HandlerDeTeste;
import br.com.fiap.soat.mecanica.execucao.support.IntegracaoTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** SqsConsumidor sobre o ProcessadorMensagemDynamo real: falha do handler deixa a mensagem na fila. */
class ProcessadorMensagemDynamoConsumidorIT extends IntegracaoTest {

    @Autowired
    private ProcessadorMensagem processador;
    @Autowired
    private HandlerDeTeste handler;
    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void limpar() {
        handler.reiniciar();
    }

    @Test
    void falhaDoHandlerNaoApagaAMensagemDaFila() throws Exception {
        handler.quandoProcessar(e -> {
            throw new IllegalStateException("falha de negocio");
        });
        String fila = criarFila("processador");
        SqsConsumidor consumidor = new SqsConsumidor(SQS, objectMapper, processador, List.of(fila), 1, 30);
        consumidor.start();
        try {
            MensagemEnvelope envelope = new MensagemEnvelope(UUID.randomUUID(), HandlerDeTeste.TIPO, 1,
                    UUID.randomUUID(), UUID.randomUUID(), "corr-dynamo", null, Instant.now(), null, null);
            SQS.sendMessage(r -> r.queueUrl(fila).messageBody(toJson(envelope)));

            await().until(() -> !handler.chamadas(envelope.messageId()).isEmpty());

            await().until(() -> mensagensEmVoo(fila) == 1);
            assertThat(mensagensNaFila(fila)).isEqualTo(1);
        } finally {
            consumidor.stop();
            SQS.deleteQueue(r -> r.queueUrl(fila));
        }
    }

    private String toJson(MensagemEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
