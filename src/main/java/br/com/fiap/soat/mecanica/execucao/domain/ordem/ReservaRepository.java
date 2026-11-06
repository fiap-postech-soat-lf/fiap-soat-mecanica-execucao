package br.com.fiap.soat.mecanica.execucao.domain.ordem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReservaRepository {

    /** Todas as linhas da ordem (leitura consistente). */
    List<Reserva> buscarPorOs(UUID osId);

    /**
     * Registra a linha na transacao aberta. So entra se a linha nao existir ou estiver {@code LIBERADA}
     * (uma nova rodada da Saga reserva de novo); senao a transacao falha com conflito.
     */
    void registrar(Reserva reserva);

    /** Registra a mudanca de status ({@code ATIVA} para {@code BAIXADA} ou {@code LIBERADA}); exige a linha {@code ATIVA}. */
    void atualizarStatus(UUID osId, UUID pecaId, StatusReserva novo, Instant agora);
}
