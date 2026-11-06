package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.mensageria;

import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TabelasDynamo;
import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TransacaoDynamo;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import static br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Atributos.data;
import static br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Atributos.n;
import static br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Atributos.s;

/**
 * Registro de mensagem processada: {@code Put} condicional ({@code attribute_not_exists(messageId)}) na
 * mesma transacao do handler, com TTL em {@code expiraEm}.
 */
@Component
public class IdempotenciaDynamo {

    private final TransacaoDynamo transacao;
    private final TabelasDynamo tabelas;
    private final Clock clock;
    private final Duration ttl;

    public IdempotenciaDynamo(TransacaoDynamo transacao, TabelasDynamo tabelas, Clock clock,
                              @Value("${app.idempotencia.ttl:P7D}") Duration ttl) {
        this.transacao = transacao;
        this.tabelas = tabelas;
        this.clock = clock;
        this.ttl = ttl;
    }

    /** Acrescenta a escrita de idempotencia a transacao aberta. */
    public void registrar(MensagemEnvelope envelope) {
        var agora = clock.instant();
        transacao.registrarIdempotencia(TransactWriteItem.builder().put(Put.builder()
                .tableName(tabelas.idempotencia())
                .item(Map.of(
                        "messageId", s(envelope.messageId().toString()),
                        "tipo", s(envelope.type()),
                        "processadaEm", s(data(agora)),
                        "expiraEm", n(agora.plus(ttl).getEpochSecond())))
                .conditionExpression("attribute_not_exists(messageId)")
                .build()).build());
    }
}
