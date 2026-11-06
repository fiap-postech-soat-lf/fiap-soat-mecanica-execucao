package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.mensageria;

import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TabelasDynamo;
import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TransacaoDynamo;
import br.com.fiap.soat.mecanica.execucao.adapters.out.messaging.DestinosMensageria;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.OutboxPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import static br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Atributos.data;
import static br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Atributos.s;

/**
 * Grava a mensagem em {@code execucao-outbox} dentro da transacao do caso de uso (sem transacao ativa,
 * falha). O atributo {@code pendente} coloca a linha no GSI esparso {@code pendentes} ate a publicacao (F5-i).
 * O destino e validado aqui, para um nome errado falhar no caso de uso e nao so na publicacao.
 */
@Component
public class OutboxDynamoAdapter implements OutboxPort {

    static final String PENDENTE = "1";

    private final TransacaoDynamo transacao;
    private final TabelasDynamo tabelas;
    private final DestinosMensageria destinos;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxDynamoAdapter(TransacaoDynamo transacao, TabelasDynamo tabelas, DestinosMensageria destinos,
                               ObjectMapper objectMapper, Clock clock) {
        this.transacao = transacao;
        this.tabelas = tabelas;
        this.destinos = destinos;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void registrar(String destino, MensagemEnvelope envelope) {
        transacao.exigirAtiva();
        destinos.resolver(destino);
        transacao.registrar(TransactWriteItem.builder().put(Put.builder()
                .tableName(tabelas.outbox())
                .item(Map.of(
                        "id", s(UUID.randomUUID().toString()),
                        "destino", s(destino),
                        "tipo", s(envelope.type()),
                        "envelope", s(serializar(envelope)),
                        "criadoEm", s(data(clock.instant())),
                        "pendente", s(PENDENTE)))
                .build()).build());
    }

    private String serializar(MensagemEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Envelope nao serializavel: " + envelope.type(), e);
        }
    }
}
