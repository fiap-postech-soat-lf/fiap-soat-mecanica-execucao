package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.mensageria;

import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TabelasDynamo;
import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TransacaoDynamo;
import br.com.fiap.soat.mecanica.execucao.adapters.out.messaging.DestinosMensageria;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxDynamoAdapterTest {

    private final DynamoDbClient dynamo = mock(DynamoDbClient.class);
    private final TransacaoDynamo transacao = new TransacaoDynamo(dynamo);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T10:00:00Z"), ZoneOffset.UTC);
    private final OutboxDynamoAdapter outbox = new OutboxDynamoAdapter(transacao, new TabelasDynamo(null, null, null, null, null, null),
            new DestinosMensageria(Map.of("execucao-eventos", "arn:aws:sns:us-east-1:000000000000:mecanica-execucao-eventos")),
            new ObjectMapper().findAndRegisterModules(), clock);

    @Test
    void semTransacaoAtivaFalha() {
        assertThatThrownBy(() -> outbox.registrar("execucao-eventos", envelope()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void destinoNaoConfiguradoFalhaNoCasoDeUso() {
        assertThatThrownBy(() -> transacao.executar(() -> outbox.registrar("inexistente", envelope())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("inexistente");
    }

    @Test
    void gravaALinhaPendenteNaTransacao() {
        MensagemEnvelope envelope = envelope();

        transacao.executar(() -> outbox.registrar("execucao-eventos", envelope));

        var captor = org.mockito.ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamo).transactWriteItems(captor.capture());
        var put = captor.getValue().transactItems().getFirst().put();
        assertThat(put.tableName()).isEqualTo("execucao-outbox");
        assertThat(put.item().get("pendente").s()).isEqualTo("1");
        assertThat(put.item().get("criadoEm").s()).isEqualTo("2026-11-06T10:00:00.000Z");
        assertThat(put.item().get("destino").s()).isEqualTo("execucao-eventos");
        assertThat(put.item().get("tipo").s()).isEqualTo("DiagnosticoAberto");
        assertThat(put.item().get("envelope").s()).contains(envelope.messageId().toString());
        assertThat(put.item()).doesNotContainKeys("reservadoAte", "expiraEm");
    }

    private static MensagemEnvelope envelope() {
        return new MensagemEnvelope(UUID.randomUUID(), "DiagnosticoAberto", 1, UUID.randomUUID(), UUID.randomUUID(),
                "corr", null, Instant.parse("2026-11-06T10:00:00Z"), null, null);
    }
}
