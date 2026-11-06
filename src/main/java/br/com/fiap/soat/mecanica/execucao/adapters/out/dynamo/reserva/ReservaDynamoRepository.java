package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.reserva;

import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Itens;
import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TabelasDynamo;
import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TransacaoDynamo;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Reserva;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.ReservaRepository;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.StatusReserva;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Atributos.data;

/** {@code execucao-reservas}: uma linha por ordem e peca (PK {@code osId}, SK {@code pecaId}). */
@Repository
public class ReservaDynamoRepository implements ReservaRepository {

    private final DynamoDbClient dynamo;
    private final TabelasDynamo tabelas;
    private final TransacaoDynamo transacao;

    public ReservaDynamoRepository(DynamoDbClient dynamo, TabelasDynamo tabelas, TransacaoDynamo transacao) {
        this.dynamo = dynamo;
        this.tabelas = tabelas;
        this.transacao = transacao;
    }

    @Override
    public List<Reserva> buscarPorOs(UUID osId) {
        return dynamo.query(r -> r.tableName(tabelas.reservas()).consistentRead(true)
                        .keyConditionExpression("osId = :o")
                        .expressionAttributeValues(Map.of(":o", AttributeValue.fromS(osId.toString()))))
                .items().stream().map(ReservaDynamoRepository::paraDominio).toList();
    }

    @Override
    public void registrar(Reserva reserva) {
        Put put = Put.builder().tableName(tabelas.reservas()).item(paraItem(reserva))
                .conditionExpression("attribute_not_exists(osId) OR #s = :liberada")
                .expressionAttributeNames(Map.of("#s", "status"))
                .expressionAttributeValues(Map.of(":liberada", AttributeValue.fromS(StatusReserva.LIBERADA.name())))
                .build();
        transacao.registrar(TransactWriteItem.builder().put(put).build());
    }

    @Override
    public void atualizarStatus(UUID osId, UUID pecaId, StatusReserva novo, Instant agora) {
        Update update = Update.builder().tableName(tabelas.reservas())
                .key(Map.of("osId", AttributeValue.fromS(osId.toString()),
                        "pecaId", AttributeValue.fromS(pecaId.toString())))
                .updateExpression("SET #s = :novo, atualizadaEm = :agora")
                .conditionExpression("#s = :ativa")
                .expressionAttributeNames(Map.of("#s", "status"))
                .expressionAttributeValues(Map.of(
                        ":novo", AttributeValue.fromS(novo.name()),
                        ":agora", AttributeValue.fromS(data(agora)),
                        ":ativa", AttributeValue.fromS(StatusReserva.ATIVA.name())))
                .build();
        transacao.registrar(TransactWriteItem.builder().update(update).build());
    }

    static Map<String, AttributeValue> paraItem(Reserva reserva) {
        Map<String, AttributeValue> item = new HashMap<>();
        Itens.uuid(item, "osId", reserva.osId());
        Itens.uuid(item, "pecaId", reserva.pecaId());
        Itens.numero(item, "quantidade", reserva.quantidade());
        Itens.texto(item, "status", reserva.status().name());
        Itens.instante(item, "reservadaEm", reserva.reservadaEm());
        Itens.instante(item, "atualizadaEm", reserva.atualizadaEm());
        return item;
    }

    static Reserva paraDominio(Map<String, AttributeValue> item) {
        return new Reserva(Itens.uuid(item, "osId"), Itens.uuid(item, "pecaId"),
                (int) Itens.numero(item, "quantidade"), StatusReserva.valueOf(Itens.texto(item, "status")),
                Itens.instante(item, "reservadaEm"), Itens.instante(item, "atualizadaEm"));
    }
}
