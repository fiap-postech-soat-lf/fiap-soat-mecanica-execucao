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
}
