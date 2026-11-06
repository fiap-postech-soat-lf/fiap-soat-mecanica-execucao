package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import static org.assertj.core.api.Assertions.assertThat;

class DynamoConfigTest {

    @Test
    void comEndpointECredenciaisUsaLocalStack() {
        DynamoConfig config = new DynamoConfig("us-east-1", "http://localhost:4566", "test", "test");

        assertThat(config.credenciais()).isInstanceOf(StaticCredentialsProvider.class);
        try (DynamoDbClient dynamo = config.dynamoDbClient()) {
            assertThat(dynamo.serviceClientConfiguration().endpointOverride()).hasValueSatisfying(
                    uri -> assertThat(uri).hasToString("http://localhost:4566"));
            assertThat(config.dynamoDbEnhancedClient(dynamo)).isNotNull();
        }
    }

    @Test
    void semEndpointUsaCadeiaPadraoDeCredenciais() {
        DynamoConfig config = new DynamoConfig("us-east-1", "", "", "");

        assertThat(config.credenciais()).isInstanceOf(DefaultCredentialsProvider.class);
        try (DynamoDbClient dynamo = config.dynamoDbClient()) {
            assertThat(dynamo.serviceClientConfiguration().endpointOverride()).isEmpty();
        }
    }

    @Test
    void relogioEUtc() {
        assertThat(new DynamoConfig("us-east-1", "", "", "").clock().getZone().getId()).isEqualTo("Z");
    }
}
