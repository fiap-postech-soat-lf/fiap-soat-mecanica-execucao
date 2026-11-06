package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;
import java.time.Clock;

/**
 * Clientes do DynamoDB, com o mesmo endpoint e as mesmas credenciais do {@code AwsConfig} (cadeia padrao
 * no EKS; endpoint e credenciais fixas com {@code app.aws.endpoint}, no LocalStack).
 */
@Configuration
public class DynamoConfig {

    private final Region region;
    private final String endpoint;
    private final String accessKeyId;
    private final String secretAccessKey;

    public DynamoConfig(
            @Value("${app.aws.region}") String region,
            @Value("${app.aws.endpoint:}") String endpoint,
            @Value("${app.aws.access-key-id:}") String accessKeyId,
            @Value("${app.aws.secret-access-key:}") String secretAccessKey) {
        this.region = Region.of(region);
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
    }

    @Bean(destroyMethod = "close")
    public DynamoDbClient dynamoDbClient() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder().region(region).credentialsProvider(credenciais());
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
    }

    /** Relogio da camada DynamoDB (datas das chaves, lease da outbox e TTLs); fica aqui para o config/ seguir igual ao esqueleto. */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    AwsCredentialsProvider credenciais() {
        if (!accessKeyId.isBlank() && !secretAccessKey.isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        }
        return DefaultCredentialsProvider.builder().build();
    }
}
