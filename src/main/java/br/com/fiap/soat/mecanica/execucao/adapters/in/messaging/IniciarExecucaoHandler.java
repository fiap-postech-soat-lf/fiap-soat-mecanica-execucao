package br.com.fiap.soat.mecanica.execucao.adapters.in.messaging;

import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemHandler;
import br.com.fiap.soat.mecanica.execucao.application.saga.ParticipanteSaga;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.IniciarExecucaoPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Comando {@code IniciarExecucao} do OS (fila execucao-comandos). */
@Component
@RequiredArgsConstructor
public class IniciarExecucaoHandler implements MensagemHandler {

    private final ParticipanteSaga participante;
    private final ObjectMapper objectMapper;

    @Override
    public String tipo() {
        return ParticipanteSaga.INICIAR_EXECUCAO;
    }

    @Override
    public void processar(MensagemEnvelope envelope) {
        participante.iniciarExecucao(envelope, PayloadsSaga.ler(objectMapper, envelope, IniciarExecucaoPayload.class));
    }
}
