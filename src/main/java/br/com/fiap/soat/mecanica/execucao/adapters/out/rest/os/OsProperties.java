package br.com.fiap.soat.mecanica.execucao.adapters.out.rest.os;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** {@code app.os.*}: URL base do OS (DNS do cluster), politica de retry (F4-c) e cache do catalogo (F5-o). */
@ConfigurationProperties("app.os")
public record OsProperties(String baseUrl, Retry retry, Catalogo catalogo) {

    public OsProperties {
        retry = retry == null ? new Retry(3, Duration.ofMillis(200), 2.0) : retry;
        catalogo = catalogo == null ? new Catalogo(Duration.ofMinutes(5)) : catalogo;
    }

    public record Retry(int maxTentativas, Duration esperaInicial, double multiplicador) {

        public Retry {
            maxTentativas = maxTentativas <= 0 ? 3 : maxTentativas;
            esperaInicial = esperaInicial == null ? Duration.ofMillis(200) : esperaInicial;
            multiplicador = multiplicador <= 0 ? 2.0 : multiplicador;
        }
    }

    public record Catalogo(Duration cacheTtl) {

        public Catalogo {
            cacheTtl = cacheTtl == null ? Duration.ofMinutes(5) : cacheTtl;
        }
    }
}
