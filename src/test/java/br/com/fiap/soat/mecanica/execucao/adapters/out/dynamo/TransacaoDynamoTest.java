package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo;

import br.com.fiap.soat.mecanica.execucao.application.transacao.ConflitoConcorrenciaException;
import br.com.fiap.soat.mecanica.execucao.application.transacao.MensagemDuplicadaException;
import br.com.fiap.soat.mecanica.execucao.domain.exception.RegraNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Traducao dos motivos de cancelamento, sem DynamoDB (o comportamento real fica no TransacaoDynamoIT). */
class TransacaoDynamoTest {

    private DynamoDbClient dynamo;
    private TransacaoDynamo transacao;

    @BeforeEach
    void preparar() {
        dynamo = mock(DynamoDbClient.class);
        transacao = new TransacaoDynamo(dynamo);
    }

    @Test
    void semEscritasNaoChamaODynamo() {
        assertThat(transacao.executar(() -> "ok")).isEqualTo("ok");

        verify(dynamo, never()).transactWriteItems(any(TransactWriteItemsRequest.class));
    }

    @Test
    void usaUmClientRequestTokenPorTentativa() {
        transacao.executar(() -> transacao.registrar(escrita("a")));
        transacao.executar(() -> transacao.registrar(escrita("a")));

        var captor = org.mockito.ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamo, org.mockito.Mockito.times(2)).transactWriteItems(captor.capture());
        assertThat(captor.getAllValues().get(0).clientRequestToken())
                .isNotEqualTo(captor.getAllValues().get(1).clientRequestToken());
    }

    @Test
    void falhaNaIdempotenciaEMensagemDuplicadaMesmoComOutrasCondicoesFalhas() {
        cancelar("ConditionalCheckFailed", "ConditionalCheckFailed");

        assertThatThrownBy(() -> transacao.executar(() -> {
            transacao.registrar(escrita("a"));
            transacao.registrarIdempotencia(escrita("msg"));
        })).isInstanceOf(MensagemDuplicadaException.class);
    }

    @Test
    void condicaoDeNegocioFalhaEConflito() {
        cancelar("ConditionalCheckFailed", "None");

        assertThatThrownBy(() -> transacao.executar(() -> {
            transacao.registrar(escrita("a"));
            transacao.registrarIdempotencia(escrita("msg"));
        })).isInstanceOf(ConflitoConcorrenciaException.class);
    }

    @Test
    void transactionConflictEConflito() {
        cancelar("TransactionConflict");

        assertThatThrownBy(() -> transacao.executar(() -> transacao.registrar(escrita("a"))))
                .isInstanceOf(ConflitoConcorrenciaException.class);
    }

    @Test
    void outrosMotivosSaoRepassadosSemTraducao() {
        cancelar("ThrottlingError");

        assertThatThrownBy(() -> transacao.executar(() -> transacao.registrar(escrita("a"))))
                .isInstanceOf(TransactionCanceledException.class);
    }

    @Test
    void maisDeCemEscritasFalhaSemChamarODynamo() {
        assertThatThrownBy(() -> transacao.executar(() -> IntStream.rangeClosed(1, 101)
                .forEach(i -> transacao.registrar(escrita("item" + i)))))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("101");

        verify(dynamo, never()).transactWriteItems(any(TransactWriteItemsRequest.class));
    }

    @Test
    void escritaForaDeTransacaoFalha() {
        assertThat(transacao.ativa()).isFalse();
        assertThatThrownBy(() -> transacao.registrar(escrita("a"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> transacao.registrarIdempotencia(escrita("a"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(transacao::exigirAtiva).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void transacaoFechaMesmoQuandoAAcaoFalha() {
        assertThatThrownBy(() -> transacao.executar(() -> {
            assertThat(transacao.ativa()).isTrue();
            throw new IllegalArgumentException("falha");
        })).isInstanceOf(IllegalArgumentException.class);

        assertThat(transacao.ativa()).isFalse();
    }

    private void cancelar(String... codigos) {
        var motivos = java.util.Arrays.stream(codigos)
                .map(c -> CancellationReason.builder().code(c).build()).toList();
        when(dynamo.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenThrow(TransactionCanceledException.builder().cancellationReasons(motivos).build());
    }

    private static TransactWriteItem escrita(String id) {
        return TransactWriteItem.builder().put(Put.builder().tableName("t")
                .item(Map.of("id", Atributos.s(id))).build()).build();
    }
}
