package br.com.fiap.soat.mecanica.execucao.adapters.in.messaging;

import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Desserializa o payload do envelope no record do handler. Payload ilegivel e erro: a mensagem volta e vai para a DLQ. */
final class PayloadsSaga {

    private PayloadsSaga() {
    }

    static <T> T ler(ObjectMapper objectMapper, MensagemEnvelope envelope, Class<T> tipo) {
        if (envelope.payload() == null) {
            throw new IllegalArgumentException("Mensagem " + envelope.type() + " sem payload");
        }
        try {
            return objectMapper.treeToValue(envelope.payload(), tipo);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Payload invalido em " + envelope.type() + ": " + e.getOriginalMessage(), e);
        }
    }
}
