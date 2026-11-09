package br.com.fiap.soat.mecanica.execucao.application.mensageria;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextoRastreamentoTest {

    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String SPAN_ID = "b7ad6b7169203331";

    @Test
    void envelopeComTraceparentRestauraTraceIdESpanIdRemoto() {
        Context contexto = ContextoRastreamento.doEnvelope(
                "{\"type\":\"X\",\"traceparent\":\"00-" + TRACE_ID + "-" + SPAN_ID + "-01\"}");

        SpanContext span = Span.fromContext(contexto).getSpanContext();
        assertThat(span.getTraceId()).isEqualTo(TRACE_ID);
        assertThat(span.getSpanId()).isEqualTo(SPAN_ID);
        assertThat(span.isRemote()).isTrue();
    }

    @Test
    void semCampoComValorInvalidoOuComJsonRuimDevolveRoot() {
        assertThat(ContextoRastreamento.doEnvelope("{\"type\":\"X\"}")).isSameAs(Context.root());
        assertThat(ContextoRastreamento.doEnvelope("{\"traceparent\":\"lixo\"}"))
                .satisfies(c -> assertThat(Span.fromContext(c).getSpanContext().isValid()).isFalse());
        assertThat(ContextoRastreamento.doEnvelope("{\"traceparent\":42}")).isSameAs(Context.root());
        assertThat(ContextoRastreamento.doEnvelope("{nao e json")).isSameAs(Context.root());
        assertThat(ContextoRastreamento.doEnvelope(null)).isSameAs(Context.root());
    }
}
