package br.com.fiap.soat.mecanica.execucao.application.ordem.usecase;

import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Alocacao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InativarAlocacaoUseCase {

    private final OrdemSupport ordens;
    private final OrdemRepository repository;
    private final TransacaoPort transacao;

    public Alocacao executar(UUID osId, UUID alocacaoId) {
        return transacao.executar(() -> {
            OrdemExecucao ordem = ordens.buscar(osId);
            Alocacao alocacao = ordem.inativarAlocacao(alocacaoId);
            repository.salvar(ordem);
            return alocacao;
        });
    }
}
