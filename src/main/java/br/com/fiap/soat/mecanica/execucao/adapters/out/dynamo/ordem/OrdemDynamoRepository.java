package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.ordem;

import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Atributos;
import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Itens;
import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TabelasDynamo;
import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TransacaoDynamo;
import br.com.fiap.soat.mecanica.execucao.domain.enums.StatusRecursoEnum;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Alocacao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.ComandoAtendido;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemRepository;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.PaginaFila;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Prestacao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.StatusOrdem;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code execucao-ordens}: a ordem, com as prestacoes e as alocacoes aninhadas, e um item so (A3). Toda escrita
 * confere e incrementa a {@code versao}. Enquanto a ordem esta na fila, o item leva {@code fila} e {@code filaOrdem}
 * (GSI esparso {@code fila}, F5-j).
 */
@Repository
public class OrdemDynamoRepository implements OrdemRepository {

    static final String FILA_ATIVA = "ATIVA";

    private final DynamoDbClient dynamo;
    private final TabelasDynamo tabelas;
    private final TransacaoDynamo transacao;

    public OrdemDynamoRepository(DynamoDbClient dynamo, TabelasDynamo tabelas, TransacaoDynamo transacao) {
        this.dynamo = dynamo;
        this.tabelas = tabelas;
        this.transacao = transacao;
    }

    @Override
    public Optional<OrdemExecucao> buscarPorId(UUID osId) {
        Map<String, AttributeValue> item = dynamo.getItem(r -> r.tableName(tabelas.ordens()).consistentRead(true)
                .key(Map.of("osId", AttributeValue.fromS(osId.toString())))).item();
        return item.isEmpty() ? Optional.empty() : Optional.of(paraDominio(item));
    }

    @Override
    public void salvar(OrdemExecucao ordem) {
        Put.Builder put = Put.builder().tableName(tabelas.ordens()).item(paraItem(ordem));
        if (ordem.getVersao() == 0) {
            put.conditionExpression("attribute_not_exists(osId)");
        } else {
            put.conditionExpression("versao = :v")
                    .expressionAttributeValues(Map.of(":v", AttributeValue.fromN(Long.toString(ordem.getVersao()))));
        }
        transacao.registrar(TransactWriteItem.builder().put(put.build()).build());
    }

    @Override
    public PaginaFila listarFila(int tamanho, String cursor) {
        QueryRequest.Builder consulta = QueryRequest.builder().tableName(tabelas.ordens())
                .indexName(TabelasDynamo.INDICE_FILA)
                .keyConditionExpression("fila = :f")
                .expressionAttributeValues(Map.of(":f", Atributos.s(FILA_ATIVA)))
                .scanIndexForward(true)
                // Um item a mais diz se ha proxima pagina, sem devolver uma pagina final vazia
                .limit(tamanho + 1);
        if (cursor != null && !cursor.isBlank()) {
            consulta.exclusiveStartKey(decodificarCursor(cursor));
        }
        List<Map<String, AttributeValue>> itens = new ArrayList<>(dynamo.query(consulta.build()).items());
        String proximo = null;
        if (itens.size() > tamanho) {
            itens = itens.subList(0, tamanho);
            proximo = codificarCursor(itens.get(tamanho - 1));
        }
        return new PaginaFila(itens.stream().map(OrdemDynamoRepository::paraDominio).toList(), proximo);
    }

    /** O cursor e a {@code LastEvaluatedKey} do indice ({@code osId} e {@code filaOrdem}), em base64 URL-safe. */
    private static String codificarCursor(Map<String, AttributeValue> item) {
        String chave = Itens.texto(item, "osId") + "|" + Itens.texto(item, "filaOrdem");
        return Base64.getUrlEncoder().withoutPadding().encodeToString(chave.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, AttributeValue> decodificarCursor(String cursor) {
        try {
            String chave = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] partes = chave.split("\\|", 2);
            if (partes.length != 2) {
                throw new IllegalArgumentException("Cursor inválido");
            }
            UUID.fromString(partes[0]);
            return Map.of("osId", Atributos.s(partes[0]), "fila", Atributos.s(FILA_ATIVA),
                    "filaOrdem", Atributos.s(partes[1]));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cursor inválido", e);
        }
    }

    public static Map<String, AttributeValue> paraItem(OrdemExecucao ordem) {
        Map<String, AttributeValue> item = new HashMap<>();
        Itens.uuid(item, "osId", ordem.getOsId());
        Itens.uuid(item, "sagaId", ordem.getSagaId());
        Itens.uuid(item, "veiculoId", ordem.getVeiculoId());
        Itens.texto(item, "placa", ordem.getPlaca());
        Itens.texto(item, "status", ordem.getStatus().name());
        Itens.instante(item, "abertaEm", ordem.getAbertaEm());
        Itens.instante(item, "diagnosticoConcluidoEm", ordem.getDiagnosticoConcluidoEm());
        Itens.instante(item, "iniciadaEm", ordem.getIniciadaEm());
        Itens.instante(item, "finalizadaEm", ordem.getFinalizadaEm());
        Itens.texto(item, "motivo", ordem.getMotivo());
        item.put("prestacoes", AttributeValue.fromL(ordem.getPrestacoes().stream()
                .map(OrdemDynamoRepository::prestacaoParaItem).map(AttributeValue::fromM).toList()));
        Map<String, AttributeValue> comandos = new LinkedHashMap<>();
        ordem.getComandosAtendidos().forEach((tipo, comando) ->
                comandos.put(tipo, AttributeValue.fromM(comandoParaItem(comando))));
        item.put("comandosAtendidos", AttributeValue.fromM(comandos));
        Itens.numero(item, "versao", ordem.getVersao() + 1);
        if (ordem.naFila()) {
            Itens.texto(item, "fila", FILA_ATIVA);
            Itens.texto(item, "filaOrdem", ordem.prioridadeFila() + "#" + Atributos.data(ordem.getAbertaEm()));
        }
        return item;
    }

    public static OrdemExecucao paraDominio(Map<String, AttributeValue> item) {
        List<Prestacao> prestacoes = item.getOrDefault("prestacoes", AttributeValue.fromL(List.of())).l().stream()
                .map(v -> prestacaoParaDominio(v.m())).toList();
        Map<String, ComandoAtendido> comandos = new LinkedHashMap<>();
        item.getOrDefault("comandosAtendidos", AttributeValue.fromM(Map.of())).m()
                .forEach((tipo, v) -> comandos.put(tipo, comandoParaDominio(v.m())));
        return OrdemExecucao.reconstruir(
                Itens.uuid(item, "osId"), Itens.uuid(item, "sagaId"), Itens.uuid(item, "veiculoId"),
                Itens.texto(item, "placa"), StatusOrdem.valueOf(Itens.texto(item, "status")),
                Itens.instante(item, "abertaEm"), Itens.instante(item, "diagnosticoConcluidoEm"),
                Itens.instante(item, "iniciadaEm"), Itens.instante(item, "finalizadaEm"),
                Itens.texto(item, "motivo"), prestacoes, comandos, Itens.numero(item, "versao"));
    }

    private static Map<String, AttributeValue> prestacaoParaItem(Prestacao prestacao) {
        Map<String, AttributeValue> item = new HashMap<>();
        Itens.uuid(item, "id", prestacao.getId());
        Itens.uuid(item, "servicoId", prestacao.getServicoId());
        Itens.texto(item, "nomeServico", prestacao.getNomeServico());
        Itens.decimal(item, "precoMaoDeObra", prestacao.getPrecoMaoDeObra());
        Itens.texto(item, "status", prestacao.getStatus().name());
        Itens.instante(item, "dataInicio", prestacao.getDataInicio());
        Itens.instante(item, "dataFim", prestacao.getDataFim());
        item.put("alocacoes", AttributeValue.fromL(prestacao.getAlocacoes().stream()
                .map(OrdemDynamoRepository::alocacaoParaItem).map(AttributeValue::fromM).toList()));
        return item;
    }

    private static Prestacao prestacaoParaDominio(Map<String, AttributeValue> item) {
        List<Alocacao> alocacoes = item.getOrDefault("alocacoes", AttributeValue.fromL(List.of())).l().stream()
                .map(v -> alocacaoParaDominio(v.m())).toList();
        return Prestacao.reconstruir(Itens.uuid(item, "id"), StatusRecursoEnum.valueOf(Itens.texto(item, "status")),
                Itens.uuid(item, "servicoId"), Itens.texto(item, "nomeServico"),
                Itens.decimal(item, "precoMaoDeObra"), Itens.instante(item, "dataInicio"),
                Itens.instante(item, "dataFim"), alocacoes);
    }

    private static Map<String, AttributeValue> alocacaoParaItem(Alocacao alocacao) {
        Map<String, AttributeValue> item = new HashMap<>();
        Itens.uuid(item, "id", alocacao.getId());
        Itens.uuid(item, "pecaId", alocacao.getPecaId());
        Itens.texto(item, "descricao", alocacao.getDescricao());
        Itens.numero(item, "quantidade", alocacao.getQuantidade());
        Itens.decimal(item, "valorUnitario", alocacao.getValorUnitario());
        Itens.texto(item, "status", alocacao.getStatus().name());
        return item;
    }

    private static Alocacao alocacaoParaDominio(Map<String, AttributeValue> item) {
        return Alocacao.reconstruir(Itens.uuid(item, "id"), StatusRecursoEnum.valueOf(Itens.texto(item, "status")),
                Itens.uuid(item, "pecaId"), Itens.texto(item, "descricao"), (int) Itens.numero(item, "quantidade"),
                Itens.decimal(item, "valorUnitario"));
    }

    private static Map<String, AttributeValue> comandoParaItem(ComandoAtendido comando) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("ids", AttributeValue.fromL(comando.ids().stream()
                .map(id -> AttributeValue.fromS(id.toString())).toList()));
        Itens.texto(item, "tipoResposta", comando.tipoResposta());
        Itens.texto(item, "payloadResposta", comando.payloadResposta());
        return item;
    }

    private static ComandoAtendido comandoParaDominio(Map<String, AttributeValue> item) {
        List<UUID> ids = new ArrayList<>();
        item.get("ids").l().forEach(v -> ids.add(UUID.fromString(v.s())));
        return new ComandoAtendido(ids, Itens.texto(item, "tipoResposta"), Itens.texto(item, "payloadResposta"));
    }
}
