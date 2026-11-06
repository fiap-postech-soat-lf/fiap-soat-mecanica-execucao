package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.reserva;

import br.com.fiap.soat.mecanica.execucao.application.transacao.ConflitoConcorrenciaException;
import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Reserva;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.ReservaRepository;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.StatusReserva;
import br.com.fiap.soat.mecanica.execucao.support.IntegracaoTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservaDynamoRepositoryIT extends IntegracaoTest {

    private static final Instant AGORA = Instant.parse("2026-11-06T12:00:00.000Z");

    @Autowired
    private ReservaRepository repository;
    @Autowired
    private TransacaoPort transacao;

    @Test
    void registraELePorOrdem() {
        UUID os = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        transacao.executar(() -> {
            repository.registrar(Reserva.nova(os, p1, 2, AGORA));
            repository.registrar(Reserva.nova(os, p2, 1, AGORA));
        });

        var reservas = repository.buscarPorOs(os);

        assertThat(reservas).hasSize(2);
        assertThat(reservas).allMatch(Reserva::ativa);
        assertThat(reservas).extracting(Reserva::quantidade).containsExactlyInAnyOrder(2, 1);
        assertThat(reservas.get(0).reservadaEm()).isEqualTo(AGORA);
        assertThat(repository.buscarPorOs(UUID.randomUUID())).isEmpty();
    }

    @Test
    void reservarDeNovoUmaLinhaAtivaFalhaComConflito() {
        UUID os = UUID.randomUUID();
        UUID peca = UUID.randomUUID();
        transacao.executar(() -> repository.registrar(Reserva.nova(os, peca, 1, AGORA)));

        assertThatThrownBy(() -> transacao.executar(() -> repository.registrar(Reserva.nova(os, peca, 1, AGORA))))
                .isInstanceOf(ConflitoConcorrenciaException.class);
    }

    @Test
    void linhaLiberadaPodeSerReservadaDeNovoNaNovaRodada() {
        UUID os = UUID.randomUUID();
        UUID peca = UUID.randomUUID();
        transacao.executar(() -> repository.registrar(Reserva.nova(os, peca, 1, AGORA)));
        transacao.executar(() -> repository.atualizarStatus(os, peca, StatusReserva.LIBERADA, AGORA.plusSeconds(1)));

        transacao.executar(() -> repository.registrar(Reserva.nova(os, peca, 3, AGORA.plusSeconds(2))));

        var reserva = repository.buscarPorOs(os).get(0);
        assertThat(reserva.status()).isEqualTo(StatusReserva.ATIVA);
        assertThat(reserva.quantidade()).isEqualTo(3);
    }

    @Test
    void baixarExigeALinhaAtiva() {
        UUID os = UUID.randomUUID();
        UUID peca = UUID.randomUUID();
        transacao.executar(() -> repository.registrar(Reserva.nova(os, peca, 1, AGORA)));
        transacao.executar(() -> repository.atualizarStatus(os, peca, StatusReserva.BAIXADA, AGORA.plusSeconds(1)));

        var reserva = repository.buscarPorOs(os).get(0);
        assertThat(reserva.status()).isEqualTo(StatusReserva.BAIXADA);
        assertThat(reserva.atualizadaEm()).isEqualTo(AGORA.plusSeconds(1));
        assertThatThrownBy(() -> transacao.executar(
                () -> repository.atualizarStatus(os, peca, StatusReserva.LIBERADA, AGORA)))
                .isInstanceOf(ConflitoConcorrenciaException.class);
        assertThatThrownBy(() -> transacao.executar(
                () -> repository.atualizarStatus(os, UUID.randomUUID(), StatusReserva.LIBERADA, AGORA)))
                .isInstanceOf(ConflitoConcorrenciaException.class);
    }

    @Test
    void reservaComQuantidadeInvalidaOuSemChaveEInvalida() {
        assertThatThrownBy(() -> Reserva.nova(UUID.randomUUID(), UUID.randomUUID(), 0, AGORA))
                .isInstanceOf(br.com.fiap.soat.mecanica.execucao.domain.exception.RegraNegocioException.class);
        assertThatThrownBy(() -> Reserva.nova(null, UUID.randomUUID(), 1, AGORA))
                .isInstanceOf(br.com.fiap.soat.mecanica.execucao.domain.exception.RegraNegocioException.class);
    }
}
