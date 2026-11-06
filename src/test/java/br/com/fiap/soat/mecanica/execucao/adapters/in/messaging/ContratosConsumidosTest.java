package br.com.fiap.soat.mecanica.execucao.adapters.in.messaging;

import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemHandler;
import br.com.fiap.soat.mecanica.execucao.application.saga.ParticipanteSaga;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.AbrirDiagnosticoPayload;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.IniciarExecucaoPayload;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.LiberarReservaPecasPayload;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.OrdemServicoCanceladaPayload;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.ReservarPecasPayload;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.MotivoLiberacao;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Contratos consumidos (copiados do {@code -os}, padrao da F4-m): cada exemplo valida contra o schema do OS e
 * desserializa no record do handler que o consome, que o entrega ao {@link ParticipanteSaga}.
 */
class ContratosConsumidosTest {

    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemaRegistryConfig(SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build()));

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final ParticipanteSaga participante = Mockito.mock(ParticipanteSaga.class);

    @Test
    void abrirDiagnostico() {
        MensagemEnvelope envelope = exemplo("AbrirDiagnostico");

        new AbrirDiagnosticoHandler(participante, objectMapper).processar(envelope);

        verify(participante).abrirDiagnostico(eq(envelope), eq(new AbrirDiagnosticoPayload(
                UUID.fromString("7d1c2a4e-3b5f-4c6d-8e9f-0a1b2c3d4e5f"),
                UUID.fromString("c3d4e5f6-a7b8-4c9d-8e0f-1a2b3c4d5e6f"), "ABC1D23")));
    }

    @Test
    void reservarPecas() {
        MensagemEnvelope envelope = exemplo("ReservarPecas");

        new ReservarPecasHandler(participante, objectMapper).processar(envelope);

        verify(participante).reservarPecas(eq(envelope), eq(new ReservarPecasPayload(
                UUID.fromString("7d1c2a4e-3b5f-4c6d-8e9f-0a1b2c3d4e5f"), List.of(
                new ReservarPecasPayload.PecaQuantidade(UUID.fromString("55555555-1111-4222-8333-666666666666"), 4),
                new ReservarPecasPayload.PecaQuantidade(UUID.fromString("77777777-4444-4555-8666-888888888888"), 1)))));
    }

    @Test
    void liberarReservaPecas() {
        MensagemEnvelope envelope = exemplo("LiberarReservaPecas");

        new LiberarReservaPecasHandler(participante, objectMapper).processar(envelope);

        verify(participante).liberarReserva(eq(envelope), eq(new LiberarReservaPecasPayload(
                UUID.fromString("7d1c2a4e-3b5f-4c6d-8e9f-0a1b2c3d4e5f"), MotivoLiberacao.ORCAMENTO_RECUSADO)));
    }

    @Test
    void iniciarExecucao() {
        MensagemEnvelope envelope = exemplo("IniciarExecucao");

        new IniciarExecucaoHandler(participante, objectMapper).processar(envelope);

        verify(participante).iniciarExecucao(eq(envelope),
                eq(new IniciarExecucaoPayload(UUID.fromString("7d1c2a4e-3b5f-4c6d-8e9f-0a1b2c3d4e5f"))));
    }

    @Test
    void ordemServicoCancelada() {
        MensagemEnvelope envelope = exemplo("OrdemServicoCancelada");

        new OrdemServicoCanceladaHandler(participante, objectMapper).processar(envelope);

        verify(participante).ordemCancelada(eq(envelope), eq(new OrdemServicoCanceladaPayload(
                UUID.fromString("7d1c2a4e-3b5f-4c6d-8e9f-0a1b2c3d4e5f"),
                UUID.fromString("5a4b3c2d-1e0f-4a9b-8c7d-6e5f4a3b2c1d"), "Orcamento recusado pelo cliente",
                java.time.Instant.parse("2026-11-03T15:00:00Z"))));
    }

    @Test
    void cadaHandlerTrataOTipoDoSeuContrato() {
        List<MensagemHandler> handlers = List.of(
                new AbrirDiagnosticoHandler(participante, objectMapper),
                new ReservarPecasHandler(participante, objectMapper),
                new LiberarReservaPecasHandler(participante, objectMapper),
                new IniciarExecucaoHandler(participante, objectMapper),
                new OrdemServicoCanceladaHandler(participante, objectMapper));

        assertThat(handlers).extracting(MensagemHandler::tipo).containsExactly("AbrirDiagnostico", "ReservarPecas",
                "LiberarReservaPecas", "IniciarExecucao", "OrdemServicoCancelada");
    }

    @Test
    void payloadAusenteOuInvalidoEErro() {
        MensagemEnvelope semPayload = new MensagemEnvelope(UUID.randomUUID(), "IniciarExecucao", 1, null, null, "c",
                null, java.time.Instant.now(), null, null);
        MensagemEnvelope invalido = new MensagemEnvelope(UUID.randomUUID(), "LiberarReservaPecas", 1, null, null, "c",
                null, java.time.Instant.now(), null, JsonNodeFactory.instance.objectNode()
                .put("osId", UUID.randomUUID().toString()).put("motivo", "NAO_EXISTE"));

        assertThatThrownBy(() -> new IniciarExecucaoHandler(participante, objectMapper).processar(semPayload))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LiberarReservaPecasHandler(participante, objectMapper).processar(invalido))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("LiberarReservaPecas");
        Mockito.verifyNoInteractions(participante);
    }

    private MensagemEnvelope exemplo(String tipo) {
        String json = ler("contratos-consumidos/exemplos/" + tipo + ".v1.json");
        List<Error> erroEnvelope = schema("contratos/envelope.schema.json")
                .validate(json, InputFormat.JSON);
        assertThat(erroEnvelope).as("envelope de " + tipo).isEmpty();
        try {
            MensagemEnvelope envelope = objectMapper.readValue(json, MensagemEnvelope.class);
            List<Error> erroPayload = schema("contratos-consumidos/" + tipo + ".v1.schema.json")
                    .validate(envelope.payload().toString(), InputFormat.JSON);
            assertThat(erroPayload).as("payload de " + tipo).isEmpty();
            assertThat(envelope.type()).isEqualTo(tipo);
            return envelope;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Schema schema(String caminho) {
        return REGISTRY.getSchema(ler(caminho), InputFormat.JSON);
    }

    private static String ler(String caminho) {
        try {
            return new ClassPathResource(caminho).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
