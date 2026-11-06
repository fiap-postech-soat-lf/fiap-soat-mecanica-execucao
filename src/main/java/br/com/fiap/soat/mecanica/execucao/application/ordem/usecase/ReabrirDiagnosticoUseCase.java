package br.com.fiap.soat.mecanica.execucao.application.ordem.usecase;

import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemRepository;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Reserva;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Reabre o diagnostico (F5-k) quando a Saga do OS voltou a ele sem mandar nada; so sem reserva ativa. */
@Service
@RequiredArgsConstructor
public class ReabrirDiagnosticoUseCase {

    private final OrdemSupport ordens;
    private final OrdemRepository repository;
    private final ReservaRepository reservas;
    private final TransacaoPort transacao;

    public OrdemExecucao executar(UUID osId) {
        return transacao.executar(() -> {
            OrdemExecucao ordem = ordens.buscar(osId);
            boolean reservaAtiva = reservas.buscarPorOs(osId).stream().anyMatch(Reserva::ativa);
            ordem.reabrirDiagnostico(reservaAtiva);
            repository.salvar(ordem);
            return ordem;
        });
    }
}
