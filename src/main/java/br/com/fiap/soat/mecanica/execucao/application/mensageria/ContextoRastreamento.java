package br.com.fiap.soat.mecanica.execucao.application.mensageria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;

import java.util.List;

/**
 * Restaura o contexto de rastreamento gravado no campo {@code traceparent} do envelope. O publicador do
 * outbox o torna o contexto atual durante o {@code publicar}, para que o span do SDK da AWS seja filho
 * da requisicao (ou do consumo) que criou a mensagem, e nao do {@code @Scheduled}.
 */
public final class ContextoRastreamento {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CAMPO = "traceparent";

    private static final TextMapGetter<String> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(String carrier) {
            return List.of(CAMPO);
        }

        @Override
        public String get(String carrier, String key) {
            return CAMPO.equals(key) ? carrier : null;
        }
    };

    private ContextoRastreamento() {
    }

    /** @return o contexto do envelope, ou {@link Context#root()} se faltar, for invalido ou o JSON for ilegivel */
    public static Context doEnvelope(String envelopeJson) {
        try {
            JsonNode campo = MAPPER.readTree(envelopeJson).path(CAMPO);
            if (!campo.isTextual()) {
                return Context.root();
            }
            return W3CTraceContextPropagator.getInstance().extract(Context.root(), campo.asText(), GETTER);
        } catch (Exception e) {
            return Context.root();
        }
    }
}
