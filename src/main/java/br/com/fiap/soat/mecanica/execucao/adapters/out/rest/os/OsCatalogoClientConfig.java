package br.com.fiap.soat.mecanica.execucao.adapters.out.rest.os;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * RestClient proprio do OS. Parte do builder do Spring (timeouts de 2 s e X-Correlation-Id do
 * {@code RestClientConfig}) e acrescenta o repasse do JWT, que nao vale para outros destinos (F4-b).
 */
@Configuration
public class OsCatalogoClientConfig {

    @Bean
    RestClient osRestClient(RestClient.Builder builder, OsProperties propriedades) {
        return builder
                .baseUrl(propriedades.baseUrl() == null ? "" : propriedades.baseUrl())
                .requestInterceptor(new RepasseAutorizacaoInterceptor())
                .build();
    }
}
