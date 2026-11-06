package br.com.fiap.soat.mecanica.execucao.application.ordem.usecase;

import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemRepository;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Prestacao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InativarPrestacaoUseCase {

    private final OrdemSupport ordens;
    private final OrdemRepository repository;
    private final TransacaoPort transacao;

    public Prestacao executar(UUID osId, UUID prestacaoId) {
        return transacao.executar(() -> {
            OrdemExecucao ordem = ordens.buscar(osId);
            Prestacao prestacao = ordem.inativarPrestacao(prestacaoId);
            repository.salvar(ordem);
            return prestacao;
        });
    }
}
