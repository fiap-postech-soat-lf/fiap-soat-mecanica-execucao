package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo;

import br.com.fiap.soat.mecanica.execucao.application.transacao.ConflitoConcorrenciaException;
import br.com.fiap.soat.mecanica.execucao.application.transacao.MensagemDuplicadaException;
import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import br.com.fiap.soat.mecanica.execucao.domain.exception.RegraNegocioException;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Transacao do DynamoDB (F5-f): os repositorios {@link #registrar(TransactWriteItem) registram} as escritas
 * (negocio, outbox e idempotencia) e o fim da chamada mais externa de {@link #executar(Supplier)} as grava num
 * unico {@code TransactWriteItems}. Cada tentativa usa um {@code clientRequestToken} novo.
 */
@Component
public class TransacaoDynamo implements TransacaoPort {

    static final int MAXIMO_ITENS = 100;
    private static final String CONDICAO_FALHOU = "ConditionalCheckFailed";
    private static final String CONFLITO = "TransactionConflict";

    private final DynamoDbClient dynamo;
    private final ThreadLocal<Contexto> atual = new ThreadLocal<>();

    public TransacaoDynamo(DynamoDbClient dynamo) {
        this.dynamo = dynamo;
    }

    @Override
    public <T> T executar(Supplier<T> acao) {
        if (atual.get() != null) {
            return acao.get();
        }
        Contexto contexto = new Contexto();
        atual.set(contexto);
        try {
            T resultado = acao.get();
            confirmar(contexto);
            return resultado;
        } finally {
            atual.remove();
        }
    }

    @Override
    public boolean ativa() {
        return atual.get() != null;
    }

    /** Acrescenta uma escrita a transacao aberta. */
    public void registrar(TransactWriteItem escrita) {
        contextoAtivo().itens.add(escrita);
    }

    /**
     * Acrescenta a escrita de idempotencia (um {@code Put} condicional). Se ela falhar na confirmacao, a
     * mensagem e duplicada e nao um conflito.
     */
    public void registrarIdempotencia(TransactWriteItem escrita) {
        Contexto contexto = contextoAtivo();
        contexto.itens.add(escrita);
        contexto.indiceIdempotencia = contexto.itens.size() - 1;
    }

    public void exigirAtiva() {
        contextoAtivo();
    }

    private Contexto contextoAtivo() {
        Contexto contexto = atual.get();
        if (contexto == null) {
            throw new IllegalStateException("Escrita fora de uma transacao: use TransacaoPort.executar");
        }
        return contexto;
    }

    private void confirmar(Contexto contexto) {
        if (contexto.itens.isEmpty()) {
            return;
        }
        if (contexto.itens.size() > MAXIMO_ITENS) {
            throw new RegraNegocioException("A operacao exige " + contexto.itens.size()
                    + " escritas, acima do limite de " + MAXIMO_ITENS + " por transacao");
        }
        try {
            dynamo.transactWriteItems(TransactWriteItemsRequest.builder()
                    .transactItems(contexto.itens)
                    .clientRequestToken(UUID.randomUUID().toString())
                    .build());
        } catch (TransactionCanceledException e) {
            throw traduzir(e, contexto);
        }
    }

    private static RuntimeException traduzir(TransactionCanceledException e, Contexto contexto) {
        List<CancellationReason> motivos = e.cancellationReasons();
        if (contexto.indiceIdempotencia >= 0 && contexto.indiceIdempotencia < motivos.size()
                && CONDICAO_FALHOU.equals(motivos.get(contexto.indiceIdempotencia).code())) {
            return new MensagemDuplicadaException("Mensagem ja processada", e);
        }
        boolean conflito = motivos.stream()
                .anyMatch(m -> CONDICAO_FALHOU.equals(m.code()) || CONFLITO.equals(m.code()));
        if (conflito) {
            return new ConflitoConcorrenciaException("Versao ou estoque mudou durante a transacao", e);
        }
        return e;
    }

    private static final class Contexto {
        private final List<TransactWriteItem> itens = new ArrayList<>();
        private int indiceIdempotencia = -1;
    }
}
