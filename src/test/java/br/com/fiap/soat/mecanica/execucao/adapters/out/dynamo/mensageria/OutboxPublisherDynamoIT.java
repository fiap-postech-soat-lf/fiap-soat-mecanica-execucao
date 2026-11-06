package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.mensageria;

import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Atributos;
import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TabelasDynamo;
import br.com.fiap.soat.mecanica.execucao.adapters.out.messaging.SnsSqsPublicador;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.OutboxPort;
import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import br.com.fiap.soat.mecanica.execucao.support.IntegracaoTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.sqs.model.Message;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * O agendador da aplicacao (1 s) tambem consome a outbox neste contexto compartilhado; os testes aceitam
 * que ele ou o publicador do teste publique a linha, e conferem que ela sai uma unica vez.
 */
class OutboxPublisherDynamoIT extends IntegracaoTest {

    @Autowired
    private OutboxPort outbox;
    @Autowired
    private TransacaoPort transacao;
    @Autowired
    private TabelasDynamo tabelas;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OutboxPublisherDynamo publisher;

    @Test
    void linhaDeTransacaoConfirmadaChegaAoTopico() {
        MensagemEnvelope envelope = envelope();

        transacao.executar(() -> outbox.registrar("teste-eventos", envelope));

        assertThat(recebidas(envelope, 1)).hasSize(1);
        var linha = linhaDe(envelope);
        await().untilAsserted(() -> assertThat(linhaDe(envelope)).doesNotContainKey("pendente"));
        assertThat(linha).isNotEmpty();
    }

    @Test
    void linhaDeTransacaoDesfeitaNaoChega() {
        MensagemEnvelope envelope = envelope();

        assertThatThrownBy(() -> transacao.executar(() -> {
            outbox.registrar("teste-eventos", envelope);
            throw new IllegalStateException("desfaz");
        })).isInstanceOf(IllegalStateException.class);

        publisher.publicarPendentes();
        assertThat(linhaDe(envelope)).isEmpty();
        assertThat(recebidas(envelope, 0)).isEmpty();
    }

    @Test
    void publicadaSaiDoGsiEGanhaExpiraEm() {
        MensagemEnvelope envelope = envelope();
        transacao.executar(() -> outbox.registrar("teste-eventos", envelope));

        await().untilAsserted(() -> {
            publisher.publicarPendentes();
            var linha = linhaDe(envelope);
            assertThat(linha).doesNotContainKeys("pendente", "reservadoAte");
            assertThat(linha.get("publicadaEm").s()).endsWith("Z");
            assertThat(Long.parseLong(linha.get("expiraEm").n()))
                    .isGreaterThan(Instant.now().plus(Duration.ofHours(23)).getEpochSecond());
        });
        recebidas(envelope, 1);
    }

    @Test
    void duasInstanciasSobreAMesmaLinhaPublicamUmaVez() throws Exception {
        MensagemEnvelope envelope = envelope();
        AtomicInteger publicacoes = new AtomicInteger();
        SnsSqsPublicador contador = mock(SnsSqsPublicador.class);
        doAnswer(i -> publicacoes.incrementAndGet()).when(contador).publicar(any(), any(), any());
        OutboxPublisherDynamo a = novo(contador);
        OutboxPublisherDynamo b = novo(contador);
        transacao.executar(() -> outbox.registrar("teste-eventos", envelope));

        CountDownLatch largada = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            var fa = pool.submit(() -> {
                largada.await();
                return a.publicarPendentes();
            });
            var fb = pool.submit(() -> {
                largada.await();
                return b.publicarPendentes();
            });
            largada.countDown();
            fa.get();
            fb.get();
        }

        int viaTeste = publicacoes.get();
        // a linha pode ter sido pega antes pelo agendador da aplicacao: entao chega ao topico de verdade
        int noTopico = recebidas(envelope, viaTeste == 0 ? 1 : 0).size();
        assertThat(viaTeste + noTopico).isEqualTo(1);
    }

    @Test
    void linhaComLeaseVigenteNaoEPublicada() {
        String id = UUID.randomUUID().toString();
        SnsSqsPublicador publicador = mock(SnsSqsPublicador.class);
        DYNAMO.putItem(r -> r.tableName(tabelas.outbox()).item(Map.of(
                "id", Atributos.s(id), "destino", Atributos.s("teste-quebrado"), "tipo", Atributos.s("X"),
                "envelope", Atributos.s("{}"), "criadoEm", Atributos.s(Atributos.data(Instant.now())),
                "pendente", Atributos.s("1"),
                "reservadoAte", Atributos.n(Instant.now().plusSeconds(3600).getEpochSecond()))));

        int publicadas = novo(publicador).publicarPendentes();

        assertThat(publicadas).isZero();
        org.mockito.Mockito.verifyNoInteractions(publicador);
        assertThat(item(tabelas.outbox(), "id", id)).containsKey("pendente");
        DYNAMO.deleteItem(r -> r.tableName(tabelas.outbox()).key(Map.of("id", Atributos.s(id))));
    }

    @Test
    void falhaNaPublicacaoMantemALinhaPendenteComLease() {
        MensagemEnvelope envelope = envelope();
        SnsSqsPublicador quebrado = mock(SnsSqsPublicador.class);
        doThrow(new IllegalStateException("sns fora")).when(quebrado).publicar(eq("teste-eventos"), any(), any());
        transacao.executar(() -> outbox.registrar("teste-eventos", envelope));

        int publicadas = novo(quebrado).publicarPendentes();

        // o agendador da aplicacao pode ter vencido a corrida; nesse caso a linha ja foi publicada de verdade
        var linha = linhaDe(envelope);
        if (publicadas == 0 && linha.containsKey("pendente")) {
            assertThat(linha).containsKey("reservadoAte");
        } else {
            assertThat(recebidas(envelope, 1)).hasSize(1);
        }
    }

    private OutboxPublisherDynamo novo(SnsSqsPublicador publicador) {
        return new OutboxPublisherDynamo(DYNAMO, tabelas, publicador, Clock.systemUTC(),
                Duration.ofSeconds(30), 25, Duration.ofDays(1));
    }

    private Map<String, AttributeValue> linhaDe(MensagemEnvelope envelope) {
        var linhas = DYNAMO.scan(r -> r.tableName(tabelas.outbox())
                .filterExpression("contains(envelope, :m)")
                .expressionAttributeValues(Map.of(":m", Atributos.s(envelope.messageId().toString())))
                .consistentRead(true)).items();
        return linhas.isEmpty() ? Map.of() : linhas.getFirst();
    }

    /** Mensagens do envelope na fila assinante do topico; espera ate {@code esperadas} chegarem (ou 3 s, se 0). */
    private List<Message> recebidas(MensagemEnvelope envelope, int esperadas) {
        List<Message> encontradas = new ArrayList<>();
        long limite = System.nanoTime() + Duration.ofSeconds(esperadas == 0 ? 3 : 15).toNanos();
        while (System.nanoTime() < limite && (esperadas == 0 || encontradas.size() < esperadas)) {
            receber(MENSAGERIA.filaAssinante()).stream()
                    .filter(m -> m.body().contains(envelope.messageId().toString()))
                    .forEach(encontradas::add);
        }
        return encontradas;
    }

    private MensagemEnvelope envelope() {
        return new MensagemEnvelope(UUID.randomUUID(), "TesteRecebido", 1, UUID.randomUUID(), UUID.randomUUID(),
                "corr-outbox", null, Instant.now(), null, objectMapper.createObjectNode());
    }
}
