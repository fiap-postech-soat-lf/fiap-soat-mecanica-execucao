package br.com.fiap.soat.mecanica.execucao.adapters.in.messaging;

import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemHandler;
import br.com.fiap.soat.mecanica.execucao.application.saga.ParticipanteSaga;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.ReservarPecasPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Comando {@code ReservarPecas} do OS (fila execucao-comandos). */
@Component
@RequiredArgsConstructor
public class ReservarPecasHandler implements MensagemHandler {

    private final ParticipanteSaga participante;
    private final ObjectMapper objectMapper;

    @Override
    public String tipo() {
        return ParticipanteSaga.RESERVAR_PECAS;
    }

    @Override
    public void processar(MensagemEnvelope envelope) {
        participante.reservarPecas(envelope, PayloadsSaga.ler(objectMapper, envelope, ReservarPecasPayload.class));
    }
}
