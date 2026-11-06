package br.com.fiap.soat.mecanica.execucao.domain.ordem;

import br.com.fiap.soat.mecanica.execucao.domain.exception.RegraNegocioException;

import java.time.Instant;
import java.util.UUID;

/** Linha de {@code execucao-reservas}: quantidade de uma peca reservada para uma ordem. */
public record Reserva(UUID osId, UUID pecaId, int quantidade, StatusReserva status,
                      Instant reservadaEm, Instant atualizadaEm) {

    public Reserva {
        if (osId == null || pecaId == null) {
            throw new RegraNegocioException("Ordem e peça são obrigatórias na reserva");
        }
        if (quantidade <= 0) {
            throw new RegraNegocioException("Quantidade reservada deve ser maior que zero");
        }
    }

    public static Reserva nova(UUID osId, UUID pecaId, int quantidade, Instant agora) {
        return new Reserva(osId, pecaId, quantidade, StatusReserva.ATIVA, agora, agora);
    }

    public boolean ativa() {
        return status == StatusReserva.ATIVA;
    }
}
