package br.com.fiap.soat.mecanica.execucao.application.ordem.usecase;

import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import br.com.fiap.soat.mecanica.execucao.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Alocacao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemRepository;
import br.com.fiap.soat.mecanica.execucao.domain.peca.Peca;
import br.com.fiap.soat.mecanica.execucao.domain.peca.PecaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Aloca uma peca a uma prestacao. Nao mexe no estoque: a reserva vem da Saga. */
@Service
@RequiredArgsConstructor
public class AlocarPecaUseCase {

    private final OrdemSupport ordens;
    private final OrdemRepository repository;
    private final PecaRepository pecas;
    private final TransacaoPort transacao;

    public Alocacao executar(UUID osId, UUID prestacaoId, UUID pecaId, int quantidade) {
        return transacao.executar(() -> {
            OrdemExecucao ordem = ordens.buscar(osId);
            Peca peca = pecas.buscarPorId(pecaId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Peça não encontrada"));
            Alocacao alocacao = ordem.alocarPeca(prestacaoId, peca, quantidade);
            repository.salvar(ordem);
            return alocacao;
        });
    }
}
