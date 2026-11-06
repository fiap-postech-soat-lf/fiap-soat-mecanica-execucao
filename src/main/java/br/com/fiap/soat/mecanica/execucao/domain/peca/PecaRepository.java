package br.com.fiap.soat.mecanica.execucao.domain.peca;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PecaRepository {

    /** Leitura consistente. */
    Optional<Peca> buscarPorId(UUID id);

    /** Catalogo inteiro (dezenas de itens), ordenado por nome. */
    List<Peca> listar();

    /**
     * Registra a escrita na transacao aberta. Peca nova entra se nao existir; peca lida so e gravada se a
     * {@code versao} continuar a mesma (senao a transacao falha com conflito, F5-g).
     */
    void salvar(Peca peca);

    /**
     * Registra na transacao a reserva de {@code quantidade} unidades (F5-e): move de "disponivel" para "reservada" so se
     * houver disponivel e a peca estiver ativa. Nao confere a {@code versao}: duas ordens diferentes so disputam o
     * disponivel. Se a condicao falhar, a transacao termina em conflito e o processador relê o estoque (F5-g).
     */
    void reservar(UUID pecaId, int quantidade);

    /** Registra a baixa da reserva no estoque ({@code IniciarExecucao}); exige reservada suficiente. */
    void baixar(UUID pecaId, int quantidade);

    /** Registra a devolucao da reserva ao disponivel; exige reservada suficiente. */
    void liberar(UUID pecaId, int quantidade);
}
