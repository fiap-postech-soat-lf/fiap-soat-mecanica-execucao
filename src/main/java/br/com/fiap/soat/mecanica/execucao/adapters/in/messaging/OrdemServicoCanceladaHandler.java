package br.com.fiap.soat.mecanica.execucao.adapters.in.messaging;

import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemHandler;
import br.com.fiap.soat.mecanica.execucao.application.saga.ParticipanteSaga;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.OrdemServicoCanceladaPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Evento {@code OrdemServicoCancelada} do OS (fila execucao-os-eventos, F5-b). */
@Component
@RequiredArgsConstructor
public class OrdemServicoCanceladaHandler implements MensagemHandler {

    private final ParticipanteSaga participante;
    private final ObjectMapper objectMapper;

    @Override
    public String tipo() {
        return "OrdemServicoCancelada";
    }

    @Override
    public void processar(MensagemEnvelope envelope) {
        participante.ordemCancelada(envelope, PayloadsSaga.ler(objectMapper, envelope, OrdemServicoCanceladaPayload.class));
    }
}
