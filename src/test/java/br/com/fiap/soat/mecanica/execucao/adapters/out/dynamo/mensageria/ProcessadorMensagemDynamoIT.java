package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.mensageria;

import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TabelasDynamo;
import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TransacaoDynamo;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.ProcessadorMensagem;
import br.com.fiap.soat.mecanica.execucao.application.transacao.ConflitoConcorrenciaException;
import br.com.fiap.soat.mecanica.execucao.support.HandlerDeTeste;
import br.com.fiap.soat.mecanica.execucao.support.IntegracaoTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Atributos.n;
import static br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Atributos.s;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessadorMensagemDynamoIT extends IntegracaoTest {

    @Autowired
    private ProcessadorMensagem processador;
    @Autowired
    private TransacaoDynamo transacao;
    @Autowired
    private TabelasDynamo tabelas;
    @Autowired
    private HandlerDeTeste handler;

    private String contador;

    @BeforeEach
    void preparar() {
        handler.reiniciar();
        contador = UUID.randomUUID().toString();
    }

    @AfterEach
    void limpar() {
        handler.reiniciar();
    }

    @Test
    void estaApontandoParaAImplementacaoDynamo() {
        assertThat(processador).isInstanceOf(ProcessadorMensagemDynamo.class);
    }

    @Test
    void aMesmaMessageIdDuasVezesTemEfeitoUmaVez() {
        handler.quandoProcessar(e -> somar());
        MensagemEnvelope envelope = envelope();

        processador.processar(envelope);
        processador.processar(envelope);

        assertThat(amostras()).isEqualTo(1);
        assertThat(item(tabelas.idempotencia(), "messageId", envelope.messageId().toString())).isNotEmpty();
    }

    @Test
    void handlerQueLancaNaoGravaNadaENaoRegistraIdempotencia() {
        handler.quandoProcessar(e -> {
            somar();
            throw new IllegalStateException("falha de negocio");
        });
        MensagemEnvelope envelope = envelope();

        assertThatThrownBy(() -> processador.processar(envelope)).isInstanceOf(IllegalStateException.class);

        assertThat(amostras()).isZero();
        assertThat(item(tabelas.idempotencia(), "messageId", envelope.messageId().toString())).isEmpty();
    }

    @Test
    void conflitoDuasVezesEDepoisSucessoTemEfeitoUmaVez() {
        AtomicInteger tentativas = new AtomicInteger();
        handler.quandoProcessar(e -> {
            somar();
            if (tentativas.incrementAndGet() < 3) {
                throw new ConflitoConcorrenciaException("conflito simulado", null);
            }
        });

        processador.processar(envelope());

        assertThat(tentativas).hasValue(3);
        assertThat(amostras()).isEqualTo(1);
    }

    @Test
    void conflitoQueNaoPassaDeixaAMensagemVoltarSemEfeito() {
        handler.quandoProcessar(e -> {
            somar();
            throw new ConflitoConcorrenciaException("conflito simulado", null);
        });

        assertThatThrownBy(() -> processador.processar(envelope()))
                .isInstanceOf(ConflitoConcorrenciaException.class);
        assertThat(amostras()).isZero();
    }

    /** Escreve na tabela de tempos com ADD: o valor final conta quantas vezes o efeito foi confirmado. */
    private void somar() {
        transacao.registrar(TransactWriteItem.builder().update(Update.builder()
                .tableName(tabelas.tempos())
                .key(Map.of("servicoId", s(contador)))
                .updateExpression("ADD amostras :um")
                .expressionAttributeValues(Map.of(":um", n(1)))
                .build()).build());
    }

    private int amostras() {
        var linha = item(tabelas.tempos(), "servicoId", contador);
        return linha.isEmpty() ? 0 : Integer.parseInt(linha.get("amostras").n());
    }

    private static MensagemEnvelope envelope() {
        return new MensagemEnvelope(UUID.randomUUID(), HandlerDeTeste.TIPO, 1, UUID.randomUUID(), UUID.randomUUID(),
                "corr-dynamo", null, Instant.now(), null, null);
    }
}
