package br.com.fiap.soat.mecanica.execucao.support;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.util.ArrayList;
import java.util.List;

/**
 * As 6 tabelas e os 2 GSIs da RFC-F4-02, com as mesmas chaves do Terraform (infra/dynamodb.tf, T06).
 * O {@code TabelasDeTesteTest} confere esta lista contra a RFC.
 */
public final class TabelasDeTeste {

    private TabelasDeTeste() {
    }

    public static List<CreateTableRequest> definicoes() {
        return List.of(
                tabela("execucao-ordens", chave("osId"),
                        indice("fila", "fila", "filaOrdem")),
                tabela("execucao-pecas", chave("pecaId")),
                tabela("execucao-reservas", chave("osId"), chaveOrdenacao("pecaId")),
                tabela("execucao-outbox", chave("id"),
                        indice("pendentes", "pendente", "criadoEm")),
                tabela("execucao-idempotencia", chave("messageId")),
                tabela("execucao-tempos", chave("servicoId")));
    }

    /** Cria as tabelas que ainda nao existem. */
    public static void criar(DynamoDbClient dynamo) {
        List<String> existentes = dynamo.listTables().tableNames();
        for (CreateTableRequest definicao : definicoes()) {
            if (!existentes.contains(definicao.tableName())) {
                dynamo.createTable(definicao);
            }
        }
    }

    private static CreateTableRequest tabela(String nome, KeySchemaElement chave, Object... extras) {
        List<KeySchemaElement> chaves = new ArrayList<>(List.of(chave));
        List<GlobalSecondaryIndex> indices = new ArrayList<>();
        for (Object extra : extras) {
            if (extra instanceof KeySchemaElement k) {
                chaves.add(k);
            } else if (extra instanceof GlobalSecondaryIndex g) {
                indices.add(g);
            }
        }
        List<String> atributos = new ArrayList<>(chaves.stream().map(KeySchemaElement::attributeName).toList());
        indices.forEach(i -> i.keySchema().forEach(k -> atributos.add(k.attributeName())));
        CreateTableRequest.Builder builder = CreateTableRequest.builder()
                .tableName(nome)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .keySchema(chaves)
                .attributeDefinitions(atributos.stream().distinct()
                        .map(a -> AttributeDefinition.builder().attributeName(a)
                                .attributeType(ScalarAttributeType.S).build()).toList());
        if (!indices.isEmpty()) {
            builder.globalSecondaryIndexes(indices);
        }
        return builder.build();
    }

    private static KeySchemaElement chave(String atributo) {
        return KeySchemaElement.builder().attributeName(atributo).keyType(KeyType.HASH).build();
    }

    private static KeySchemaElement chaveOrdenacao(String atributo) {
        return KeySchemaElement.builder().attributeName(atributo).keyType(KeyType.RANGE).build();
    }

    private static GlobalSecondaryIndex indice(String nome, String pk, String sk) {
        return GlobalSecondaryIndex.builder().indexName(nome)
                .keySchema(chave(pk), chaveOrdenacao(sk))
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                .build();
    }
}
