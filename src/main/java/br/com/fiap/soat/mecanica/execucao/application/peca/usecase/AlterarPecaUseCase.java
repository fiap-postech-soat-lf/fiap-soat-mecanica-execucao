package br.com.fiap.soat.mecanica.execucao.application.peca.usecase;

import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import br.com.fiap.soat.mecanica.execucao.domain.peca.Peca;
import br.com.fiap.soat.mecanica.execucao.domain.peca.PecaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Altera os dados e o estoque. O estoque nunca fica abaixo do reservado (422) e, se uma reserva acontecer
 * entre a leitura e a escrita, a versao da peca falha a transacao (409).
 */
@Service
@RequiredArgsConstructor
public class AlterarPecaUseCase {

    private final PecaRepository repository;
    private final BuscarPecaPorIdUseCase buscarPecaPorId;
    private final TransacaoPort transacao;

    public Peca executar(UUID id, String nome, String marca, BigDecimal valorUnitario, Integer qtdEstoque) {
        return transacao.executar(() -> {
            Peca peca = buscarPecaPorId.executar(id);
            peca.alterar(nome, marca, valorUnitario, qtdEstoque);
            repository.salvar(peca);
            return peca;
        });
    }
}
