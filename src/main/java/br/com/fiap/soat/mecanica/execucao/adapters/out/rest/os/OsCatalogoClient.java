package br.com.fiap.soat.mecanica.execucao.adapters.out.rest.os;

import br.com.fiap.soat.mecanica.execucao.application.ordem.exception.IntegracaoOsException;
import br.com.fiap.soat.mecanica.execucao.application.ordem.exception.OsIndisponivelException;
import br.com.fiap.soat.mecanica.execucao.application.ordem.exception.ServicoInvalidoException;
import br.com.fiap.soat.mecanica.execucao.application.ordem.port.CatalogoServicosPort;
import br.com.fiap.soat.mecanica.execucao.application.ordem.port.ServicoCatalogo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Consulta o servico no catalogo do OS (F5-o) com timeout de 2 s e retry programatico (F4-c): 3 tentativas,
 * backoff exponencial, so para erro de I/O, timeout ou 5xx. 404 e 401/403 nao sao repetidos. Guarda em cache
 * (Caffeine) <b>so as respostas 200</b>. E chamado so a partir de uma requisicao do mecanico, que traz o JWT
 * repassado ao OS.
 */
@Slf4j
@Component
public class OsCatalogoClient implements CatalogoServicosPort {

    private static final String STATUS_ATIVO = "ATIVO";

    private final RestClient restClient;
    private final Retry retry;
    private final Cache<UUID, ServicoCatalogo> cache;

    public OsCatalogoClient(RestClient osRestClient, OsProperties propriedades) {
        this.restClient = osRestClient;
        OsProperties.Retry config = propriedades.retry();
        this.retry = Retry.of("os-catalogo", RetryConfig.custom()
                .maxAttempts(config.maxTentativas())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(config.esperaInicial(), config.multiplicador()))
                .retryOnException(OsCatalogoClient::repetivel)
                .build());
        this.cache = Caffeine.newBuilder().expireAfterWrite(propriedades.catalogo().cacheTtl())
                .maximumSize(1_000).build();
    }

    @Override
    public ServicoCatalogo consultar(UUID servicoId) {
        ServicoCatalogo servico = cache.getIfPresent(servicoId);
        if (servico == null) {
            servico = buscarNoOs(servicoId);
            cache.put(servicoId, servico);
        }
        if (!servico.ativo()) {
            throw new ServicoInvalidoException("Serviço inativo: " + servicoId);
        }
        return servico;
    }

    private ServicoCatalogo buscarNoOs(UUID servicoId) {
        try {
            ServicoResponse resposta = Retry.decorateSupplier(retry, () -> buscar(servicoId)).get();
            if (resposta == null || resposta.id() == null) {
                throw new ServicoInvalidoException("Serviço não encontrado: " + servicoId);
            }
            return new ServicoCatalogo(resposta.id(), resposta.nome(), STATUS_ATIVO.equalsIgnoreCase(resposta.status()));
        } catch (HttpClientErrorException.NotFound e) {
            throw new ServicoInvalidoException("Serviço não encontrado: " + servicoId);
        } catch (HttpClientErrorException e) {
            log.atWarn().addKeyValue("event", "os_recusou_chamada")
                    .addKeyValue("status", e.getStatusCode().value()).log("OS recusou a chamada");
            throw new IntegracaoOsException("O OS recusou a chamada (HTTP %d)"
                    .formatted(e.getStatusCode().value()), e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.atWarn().addKeyValue("event", "os_indisponivel")
                    .addKeyValue("exceptionType", e.getClass().getSimpleName())
                    .log("OS indisponível após os retries");
            throw new OsIndisponivelException("OS indisponível", e);
        }
    }

    private ServicoResponse buscar(UUID servicoId) {
        return restClient.get().uri("/servicos/{id}", servicoId).retrieve().body(ServicoResponse.class);
    }

    private static boolean repetivel(Throwable erro) {
        return erro instanceof ResourceAccessException || erro instanceof HttpServerErrorException;
    }
}
