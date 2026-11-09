package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.mensageria;

import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TabelasDynamo;
import br.com.fiap.soat.mecanica.execucao.adapters.out.messaging.SnsSqsPublicador;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.ContextoRastreamento;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Atributos.data;
import static br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Atributos.n;
import static br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Atributos.s;

/**
 * Publica as mensagens pendentes da outbox a cada 1 s (F5-i). Le o GSI esparso {@code pendentes}, em ordem
 * de {@code criadoEm}, e reserva cada linha com um lease condicional antes de publicar: duas replicas nao
 * publicam a mesma linha ao mesmo tempo. Entrega pelo menos uma vez; uma duplicata eventual e absorvida
 * pela idempotencia do consumidor. Falha na publicacao deixa o lease vencer (nova tentativa depois dele).
 */
@Slf4j
@Component
public class OutboxPublisherDynamo {

    private final DynamoDbClient dynamo;
    private final TabelasDynamo tabelas;
    private final SnsSqsPublicador publicador;
    private final Clock clock;
    private final Duration lease;
    private final int lote;
    private final Duration ttlPublicada;

    public OutboxPublisherDynamo(DynamoDbClient dynamo, TabelasDynamo tabelas, SnsSqsPublicador publicador,
                                 Clock clock,
                                 @Value("${app.outbox.lease:PT30S}") Duration lease,
                                 @Value("${app.outbox.lote:25}") int lote,
                                 @Value("${app.outbox.ttl-publicada:P1D}") Duration ttlPublicada) {
        this.dynamo = dynamo;
        this.tabelas = tabelas;
        this.publicador = publicador;
        this.clock = clock;
        this.lease = lease;
        this.lote = lote;
        this.ttlPublicada = ttlPublicada;
    }

    @Scheduled(fixedDelay = 1000)
    public void agendado() {
        try {
            publicarPendentes();
        } catch (RuntimeException e) {
            log.atWarn().addKeyValue("event", "outbox_rodada_falhou")
                    .addKeyValue("erro", e.toString())
                    .log("Falha ao ler a outbox; nova tentativa na proxima rodada");
        }
    }

    /** @return quantas mensagens foram publicadas nesta rodada */
    public int publicarPendentes() {
        List<Map<String, AttributeValue>> pendentes = dynamo.query(r -> r
                .tableName(tabelas.outbox())
                .indexName(TabelasDynamo.INDICE_PENDENTES)
                .keyConditionExpression("pendente = :p")
                .expressionAttributeValues(Map.of(":p", s(OutboxDynamoAdapter.PENDENTE)))
                .scanIndexForward(true)
                .limit(lote)).items();
        int publicadas = 0;
        for (Map<String, AttributeValue> linha : pendentes) {
            if (reservar(linha) && publicar(linha)) {
                publicadas++;
            }
        }
        return publicadas;
    }

    private boolean reservar(Map<String, AttributeValue> linha) {
        long agora = clock.instant().getEpochSecond();
        try {
            dynamo.updateItem(r -> r
                    .tableName(tabelas.outbox())
                    .key(Map.of("id", linha.get("id")))
                    .updateExpression("SET reservadoAte = :ate")
                    .conditionExpression("attribute_exists(pendente) AND "
                            + "(attribute_not_exists(reservadoAte) OR reservadoAte < :agora)")
                    .expressionAttributeValues(Map.of(
                            ":ate", n(agora + lease.getSeconds()),
                            ":agora", n(agora))));
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    private boolean publicar(Map<String, AttributeValue> linha) {
        String destino = linha.get("destino").s();
        String tipo = linha.get("tipo").s();
        try (Scope ignored = ContextoRastreamento.doEnvelope(linha.get("envelope").s()).makeCurrent()) {
            publicador.publicar(destino, tipo, linha.get("envelope").s());
        } catch (RuntimeException e) {
            log.atWarn().addKeyValue("event", "outbox_publicacao_falhou")
                    .addKeyValue("destino", destino)
                    .addKeyValue("tipo", tipo)
                    .addKeyValue("id", linha.get("id").s())
                    .log("Falha ao publicar mensagem da outbox: {}", e.getMessage());
            return false;
        }
        var agora = clock.instant();
        dynamo.updateItem(r -> r
                .tableName(tabelas.outbox())
                .key(Map.of("id", linha.get("id")))
                .updateExpression("REMOVE pendente, reservadoAte SET publicadaEm = :agora, expiraEm = :ttl")
                .expressionAttributeValues(Map.of(
                        ":agora", s(data(agora)),
                        ":ttl", n(agora.plus(ttlPublicada).getEpochSecond()))));
        return true;
    }
}
