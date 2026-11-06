package br.com.fiap.soat.mecanica.execucao.application.transacao;

import java.util.function.Supplier;

/**
 * Fronteira transacional dos casos de uso e dos handlers (equivalente ao {@code @Transactional} do
 * esqueleto SQL). Todas as escritas feitas dentro de {@link #executar(Supplier)} sao confirmadas juntas,
 * ou nenhuma e.
 */
public interface TransacaoPort {

    /**
     * Executa a acao numa transacao. Chamada aninhada entra na transacao de fora (so a de fora confirma).
     * Excecao lancada pela acao desfaz tudo.
     *
     * @throws ConflitoConcorrenciaException se a versao da ordem ou o estoque mudou antes da confirmacao
     * @throws MensagemDuplicadaException    se a escrita de idempotencia da transacao ja existia
     */
    <T> T executar(Supplier<T> acao);

    default void executar(Runnable acao) {
        executar(() -> {
            acao.run();
            return null;
        });
    }

    /** {@code true} se ha uma transacao aberta na thread atual. */
    boolean ativa();
}
