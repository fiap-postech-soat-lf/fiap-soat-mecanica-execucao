package br.com.fiap.soat.mecanica.execucao.support;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Confere as tabelas de teste contra a RFC-F4-02: 6 tabelas on-demand e 2 GSIs esparsos com projecao ALL. */
class TabelasDeTesteTest {

    @Test
    void defineAsTabelasEChavesDaRfc() {
        Map<String, String> chaves = Map.of(
                "execucao-ordens", "osId",
                "execucao-pecas", "pecaId",
                "execucao-reservas", "osId+pecaId",
                "execucao-outbox", "id",
                "execucao-idempotencia", "messageId",
                "execucao-tempos", "servicoId");

        List<CreateTableRequest> definicoes = TabelasDeTeste.definicoes();

        assertThat(definicoes).extracting(CreateTableRequest::tableName).containsExactlyInAnyOrderElementsOf(chaves.keySet());
        definicoes.forEach(d -> {
            assertThat(d.billingMode()).isEqualTo(BillingMode.PAY_PER_REQUEST);
            assertThat(String.join("+", d.keySchema().stream().map(KeySchemaElement::attributeName).toList()))
                    .isEqualTo(chaves.get(d.tableName()));
        });
    }

    @Test
    void defineOsDoisGsisDaRfc() {
        var indices = TabelasDeTeste.definicoes().stream()
                .flatMap(d -> d.globalSecondaryIndexes().stream().map(i -> d.tableName() + "/" + i.indexName() + "/"
                        + i.keySchema().stream().map(KeySchemaElement::attributeName).toList() + "/"
                        + i.projection().projectionTypeAsString()))
                .toList();

        assertThat(indices).containsExactlyInAnyOrder(
                "execucao-ordens/fila/[fila, filaOrdem]/ALL",
                "execucao-outbox/pendentes/[pendente, criadoEm]/ALL");
    }
}
