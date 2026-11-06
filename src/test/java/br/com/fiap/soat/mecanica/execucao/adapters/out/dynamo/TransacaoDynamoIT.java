package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo;

import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.mensageria.IdempotenciaDynamo;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.application.transacao.ConflitoConcorrenciaException;
import br.com.fiap.soat.mecanica.execucao.application.transacao.MensagemDuplicadaException;
import br.com.fiap.soat.mecanica.execucao.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.execucao.support.IntegracaoTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Atributos.n;
import static br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Atributos.s;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransacaoDynamoIT extends IntegracaoTest {

    @Autowired
    private TransacaoDynamo transacao;
    @Autowired
    private IdempotenciaDynamo idempotencia;
    @Autowired
    private TabelasDynamo tabelas;

    @Test
    void confirmaVariasEscritasJuntas() {
        String a = id();
        String b = id();

        String resultado = transacao.executar(() -> {
            transacao.registrar(gravar(a));
            transacao.registrar(gravar(b));
            return "ok";
        });

        assertThat(resultado).isEqualTo("ok");
        assertThat(existe(a)).isTrue();
        assertThat(existe(b)).isTrue();
    }

    @Test
    void excecaoNoMeioNaoGravaNada() {
        String a = id();

        assertThatThrownBy(() -> transacao.executar(() -> {
            transacao.registrar(gravar(a));
            throw new IllegalStateException("falha no meio");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(existe(a)).isFalse();
        assertThat(transacao.ativa()).isFalse();
    }

    @Test
    void chamadaAninhadaEntraNaTransacaoDeFora() {
        String a = id();
        String b = id();

        transacao.executar(() -> {
            transacao.executar(() -> transacao.registrar(gravar(a)));
            assertThat(existe(a)).as("so a transacao de fora confirma").isFalse();
            transacao.registrar(gravar(b));
        });

        assertThat(existe(a)).isTrue();
        assertThat(existe(b)).isTrue();
    }

    @Test
    void maisDeCemEscritasFalhaENadaEGravado() {
        String primeira = id();

        assertThatThrownBy(() -> transacao.executar(() -> {
            transacao.registrar(gravar(primeira));
            IntStream.range(0, 100).forEach(i -> transacao.registrar(gravar(id())));
        })).isInstanceOf(RegraNegocioException.class);

        assertThat(existe(primeira)).isFalse();
    }

    @Test
    void idempotenciaDuplicadaLancaMensagemDuplicadaESemEfeito() {
        MensagemEnvelope envelope = envelope();
        transacao.executar(() -> idempotencia.registrar(envelope));
        String a = id();

        assertThatThrownBy(() -> transacao.executar(() -> {
            transacao.registrar(gravar(a));
            idempotencia.registrar(envelope);
        })).isInstanceOf(MensagemDuplicadaException.class);

        assertThat(existe(a)).isFalse();
        var registro = item(tabelas.idempotencia(), "messageId", envelope.messageId().toString());
        assertThat(registro.get("tipo").s()).isEqualTo("TesteRecebido");
        assertThat(Long.parseLong(registro.get("expiraEm").n())).isGreaterThan(Instant.now().getEpochSecond());
    }

    @Test
    void versaoErradaLancaConflito() {
        String peca = id();
        DYNAMO.putItem(r -> r.tableName(tabelas.pecas()).item(Map.of("pecaId", s(peca), "versao", n(1))));
        String a = id();

        assertThatThrownBy(() -> transacao.executar(() -> {
            transacao.registrar(gravar(a));
            transacao.registrar(TransactWriteItem.builder().update(Update.builder()
                    .tableName(tabelas.pecas())
                    .key(Map.of("pecaId", s(peca)))
                    .updateExpression("SET versao = versao + :um")
                    .conditionExpression("versao = :v")
                    .expressionAttributeValues(Map.of(":um", n(1), ":v", n(0)))
                    .build()).build());
        })).isInstanceOf(ConflitoConcorrenciaException.class);

        assertThat(existe(a)).isFalse();
        assertThat(item(tabelas.pecas(), "pecaId", peca).get("versao").n()).isEqualTo("1");
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private boolean existe(String servicoId) {
        return !item(tabelas.tempos(), "servicoId", servicoId).isEmpty();
    }

    private TransactWriteItem gravar(String servicoId) {
        return TransactWriteItem.builder().put(Put.builder().tableName(tabelas.tempos())
                .item(Map.of("servicoId", s(servicoId), "amostras", n(1)))
                .conditionExpression("attribute_not_exists(servicoId)").build()).build();
    }

    private static MensagemEnvelope envelope() {
        return new MensagemEnvelope(UUID.randomUUID(), "TesteRecebido", 1, UUID.randomUUID(), UUID.randomUUID(),
                "corr", null, Instant.now(), null, null);
    }
}
