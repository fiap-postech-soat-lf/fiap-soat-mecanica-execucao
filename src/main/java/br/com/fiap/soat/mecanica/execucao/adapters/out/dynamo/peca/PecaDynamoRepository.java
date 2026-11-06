package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.peca;

import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Itens;
import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TabelasDynamo;
import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TransacaoDynamo;
import br.com.fiap.soat.mecanica.execucao.domain.enums.StatusRecursoEnum;
import br.com.fiap.soat.mecanica.execucao.domain.peca.Peca;
import br.com.fiap.soat.mecanica.execucao.domain.peca.PecaRepository;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** {@code execucao-pecas}: item por peca, com a {@code versao} conferida em toda escrita feita pelo REST. */
@Repository
public class PecaDynamoRepository implements PecaRepository {

    private final DynamoDbClient dynamo;
    private final TabelasDynamo tabelas;
    private final TransacaoDynamo transacao;

    public PecaDynamoRepository(DynamoDbClient dynamo, TabelasDynamo tabelas, TransacaoDynamo transacao) {
        this.dynamo = dynamo;
        this.tabelas = tabelas;
        this.transacao = transacao;
    }

    @Override
    public Optional<Peca> buscarPorId(UUID id) {
        Map<String, AttributeValue> item = dynamo.getItem(r -> r.tableName(tabelas.pecas()).consistentRead(true)
                .key(Map.of("pecaId", AttributeValue.fromS(id.toString())))).item();
        return item.isEmpty() ? Optional.empty() : Optional.of(paraDominio(item));
    }

    @Override
    public List<Peca> listar() {
        List<Peca> pecas = new ArrayList<>();
        Map<String, AttributeValue> inicio = null;
        do {
            ScanRequest.Builder requisicao = ScanRequest.builder().tableName(tabelas.pecas()).consistentRead(true);
            if (inicio != null) {
                requisicao.exclusiveStartKey(inicio);
            }
            ScanResponse resposta = dynamo.scan(requisicao.build());
            resposta.items().forEach(item -> pecas.add(paraDominio(item)));
            inicio = resposta.hasLastEvaluatedKey() ? resposta.lastEvaluatedKey() : null;
        } while (inicio != null);
        pecas.sort(Comparator.comparing(Peca::getNome, String.CASE_INSENSITIVE_ORDER));
        return pecas;
    }

    @Override
    public void salvar(Peca peca) {
        Map<String, AttributeValue> item = paraItem(peca);
        Put.Builder put = Put.builder().tableName(tabelas.pecas()).item(item);
        if (peca.getVersao() == 0) {
            put.conditionExpression("attribute_not_exists(pecaId)");
        } else {
            put.conditionExpression("versao = :v")
                    .expressionAttributeValues(Map.of(":v", AttributeValue.fromN(Long.toString(peca.getVersao()))));
        }
        transacao.registrar(TransactWriteItem.builder().put(put.build()).build());
    }

    static Map<String, AttributeValue> paraItem(Peca peca) {
        Map<String, AttributeValue> item = new HashMap<>();
        Itens.uuid(item, "pecaId", peca.getId());
        Itens.texto(item, "nome", peca.getNome());
        Itens.texto(item, "marca", peca.getMarca());
        Itens.decimal(item, "valorUnitario", peca.getValorUnitario());
        Itens.numero(item, "qtdEstoque", peca.getQtdEstoque());
        Itens.numero(item, "qtdReservada", peca.getQtdReservada());
        Itens.numero(item, "qtdDisponivel", peca.getQtdDisponivel());
        Itens.texto(item, "status", peca.getStatus().name());
        Itens.numero(item, "versao", peca.getVersao() + 1);
        return item;
    }

    static Peca paraDominio(Map<String, AttributeValue> item) {
        return Peca.reconstruir(
                Itens.uuid(item, "pecaId"),
                StatusRecursoEnum.valueOf(Itens.texto(item, "status")),
                Itens.texto(item, "nome"),
                Itens.texto(item, "marca"),
                Itens.decimal(item, "valorUnitario"),
                (int) Itens.numero(item, "qtdEstoque"),
                (int) Itens.numero(item, "qtdReservada"),
                (int) Itens.numero(item, "qtdDisponivel"),
                Itens.numero(item, "versao"));
    }
}
