package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.tempo;

import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Atributos;
import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Itens;
import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TabelasDynamo;
import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TransacaoDynamo;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.TempoMedio;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.TempoRepository;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** {@code execucao-tempos}: um item por servico, com {@code totalMinutos} e {@code amostras} acumulados por {@code ADD} (F5-d). */
@Repository
public class TempoDynamoRepository implements TempoRepository {

    private final DynamoDbClient dynamo;
    private final TabelasDynamo tabelas;
    private final TransacaoDynamo transacao;

    public TempoDynamoRepository(DynamoDbClient dynamo, TabelasDynamo tabelas, TransacaoDynamo transacao) {
        this.dynamo = dynamo;
        this.tabelas = tabelas;
        this.transacao = transacao;
    }

    @Override
    public void acumular(UUID servicoId, String nomeServico, long minutos) {
        Update update = Update.builder().tableName(tabelas.tempos())
                .key(Map.of("servicoId", Atributos.s(servicoId.toString())))
                .updateExpression("SET nomeServico = :nome ADD totalMinutos :m, amostras :um")
                .expressionAttributeValues(Map.of(
                        ":nome", Atributos.s(nomeServico),
                        ":m", Atributos.n(minutos),
                        ":um", Atributos.n(1)))
                .build();
        transacao.registrar(TransactWriteItem.builder().update(update).build());
    }

    @Override
    public Optional<TempoMedio> buscar(UUID servicoId) {
        Map<String, AttributeValue> item = dynamo.getItem(r -> r.tableName(tabelas.tempos()).consistentRead(true)
                .key(Map.of("servicoId", Atributos.s(servicoId.toString())))).item();
        return item.isEmpty() ? Optional.empty() : Optional.of(paraDominio(item));
    }

    @Override
    public List<TempoMedio> listar() {
        ScanRequest consulta = ScanRequest.builder().tableName(tabelas.tempos()).consistentRead(true).build();
        return dynamo.scanPaginator(consulta).items().stream()
                .map(TempoDynamoRepository::paraDominio)
                // Item sem nome (nao deve existir) vai para o fim em vez de derrubar a listagem
                .sorted(Comparator.comparing(TempoMedio::nomeServico,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    static TempoMedio paraDominio(Map<String, AttributeValue> item) {
        return new TempoMedio(Itens.uuid(item, "servicoId"), Itens.texto(item, "nomeServico"),
                Itens.numero(item, "totalMinutos"), Itens.numero(item, "amostras"));
    }
}
