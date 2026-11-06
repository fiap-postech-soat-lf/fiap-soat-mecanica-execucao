package br.com.fiap.soat.mecanica.execucao.support;

import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TabelasDynamo;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.ProcessadorMensagem;
import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemRepository;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Prestacao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.ReservaRepository;
import br.com.fiap.soat.mecanica.execucao.domain.peca.Peca;
import br.com.fiap.soat.mecanica.execucao.domain.peca.PecaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Base dos ITs da Saga: monta pecas e ordens direto pelos repositorios, entrega comandos ao
 * {@link ProcessadorMensagem} (como o consumidor SQS faz) e le as respostas na outbox, que e onde elas nascem na mesma
 * transacao do handler. Como as tabelas sao compartilhadas, cada teste cria as proprias pecas e ordens.
 */
public abstract class SagaIntegracaoTest extends IntegracaoTest {

    @Autowired
    protected ProcessadorMensagem processador;
    @Autowired
    protected PecaRepository pecas;
    @Autowired
    protected OrdemRepository ordens;
    @Autowired
    protected ReservaRepository reservas;
    @Autowired
    protected TransacaoPort transacao;
    @Autowired
    protected TabelasDynamo tabelas;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected Clock clock;

    protected Peca criarPeca(int estoque) {
        Peca peca = new Peca("Peca " + UUID.randomUUID(), "Marca", new BigDecimal("10.00"), estoque);
        transacao.executar(() -> pecas.salvar(peca));
        return peca;
    }

    /** Ordem {@code EM_DIAGNOSTICO} com uma prestacao e a peca alocada. */
    protected OrdemExecucao ordemEmDiagnostico(Peca peca, int quantidade) {
        OrdemExecucao ordem = OrdemExecucao.abrir(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "ABC1D23", clock);
        Prestacao prestacao = ordem.incluirPrestacao(UUID.randomUUID(), "Servico", new BigDecimal("50.00"));
        ordem.alocarPeca(prestacao.getId(), peca, quantidade);
        transacao.executar(() -> ordens.salvar(ordem));
        return recarregar(ordem.getOsId());
    }

    /** Ordem {@code DIAGNOSTICO_CONCLUIDO}, pronta para o {@code ReservarPecas}. */
    protected OrdemExecucao ordemComDiagnostico(Peca peca, int quantidade) {
        OrdemExecucao ordem = ordemEmDiagnostico(peca, quantidade);
        ordem.concluirDiagnostico(clock);
        transacao.executar(() -> ordens.salvar(ordem));
        return recarregar(ordem.getOsId());
    }

    protected OrdemExecucao recarregar(UUID osId) {
        return ordens.buscarPorId(osId).orElseThrow();
    }

    protected Peca peca(UUID pecaId) {
        return pecas.buscarPorId(pecaId).orElseThrow();
    }

    protected MensagemEnvelope comando(String tipo, UUID osId, UUID sagaId, Map<String, ?> payload) {
        return comando(tipo, osId, sagaId, null, payload);
    }

    protected MensagemEnvelope comando(String tipo, UUID osId, UUID sagaId, UUID causationId, Map<String, ?> payload) {
        return new MensagemEnvelope(UUID.randomUUID(), tipo, 1, sagaId, osId, "corr-" + osId, causationId,
                Instant.now(), null, objectMapper.valueToTree(payload));
    }

    protected MensagemEnvelope reservar(OrdemExecucao ordem, Peca peca, int quantidade) {
        return comando("ReservarPecas", ordem.getOsId(), ordem.getSagaId(), Map.of("osId", ordem.getOsId(),
                "pecas", List.of(Map.of("pecaId", peca.getId(), "quantidade", quantidade))));
    }

    protected MensagemEnvelope liberar(OrdemExecucao ordem, String motivo) {
        return comando("LiberarReservaPecas", ordem.getOsId(), ordem.getSagaId(),
                Map.of("osId", ordem.getOsId(), "motivo", motivo));
    }

    protected MensagemEnvelope iniciar(OrdemExecucao ordem) {
        return comando("IniciarExecucao", ordem.getOsId(), ordem.getSagaId(), Map.of("osId", ordem.getOsId()));
    }

    /** Envelopes de resposta registrados na outbox para a ordem, do mais antigo para o mais novo. */
    protected List<JsonNode> respostas(UUID osId) {
        ScanRequest consulta = ScanRequest.builder().tableName(tabelas.outbox()).consistentRead(true)
                .filterExpression("contains(envelope, :o)")
                .expressionAttributeValues(Map.of(":o", AttributeValue.fromS(osId.toString()))).build();
        return DYNAMO.scanPaginator(consulta).items().stream()
                .sorted(Comparator.comparing((Map<String, AttributeValue> i) -> i.get("criadoEm").s()))
                .map(i -> lerJson(i.get("envelope").s()))
                .toList();
    }

    protected List<String> tiposRespondidos(UUID osId) {
        return respostas(osId).stream().map(e -> e.path("type").asText()).toList();
    }

    private JsonNode lerJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
