package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.mensageria;

import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemHandler;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.ProcessadorMensagem;
import br.com.fiap.soat.mecanica.execucao.application.transacao.ConflitoConcorrenciaException;
import br.com.fiap.soat.mecanica.execucao.application.transacao.MensagemDuplicadaException;
import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Despacha para o handler do tipo e registra a idempotencia na mesma transacao (F5-g). Mensagem duplicada
 * e concluida sem efeito; conflito de versao ou de estoque repete o handler (com nova leitura) ate
 * {@value #MAXIMO_TENTATIVAS} vezes e, esgotado, deixa a mensagem voltar para a fila.
 */
@Slf4j
@Component
public class ProcessadorMensagemDynamo implements ProcessadorMensagem {

    static final int MAXIMO_TENTATIVAS = 3;

    private final TransacaoPort transacao;
    private final IdempotenciaDynamo idempotencia;
    private final Map<String, MensagemHandler> handlers;

    public ProcessadorMensagemDynamo(TransacaoPort transacao, IdempotenciaDynamo idempotencia,
                                     List<MensagemHandler> handlers) {
        this.transacao = transacao;
        this.idempotencia = idempotencia;
        this.handlers = handlers.stream().collect(Collectors.toMap(MensagemHandler::tipo, Function.identity(),
                (a, b) -> {
                    throw new IllegalStateException("Dois handlers para o tipo " + a.tipo());
                }));
    }

    @Override
    public void processar(MensagemEnvelope envelope) {
        MensagemHandler handler = handlers.get(envelope.type());
        if (handler == null) {
            log.atInfo().addKeyValue("event", "tipo_desconhecido")
                    .addKeyValue("tipo", envelope.type())
                    .log("Tipo de mensagem sem handler neste servico");
            return;
        }
        for (int tentativa = 1; ; tentativa++) {
            try {
                transacao.executar(() -> {
                    handler.processar(envelope);
                    idempotencia.registrar(envelope);
                });
                return;
            } catch (MensagemDuplicadaException e) {
                log.atInfo().addKeyValue("event", "mensagem_duplicada")
                        .addKeyValue("tipo", envelope.type())
                        .log("Mensagem ja processada; ignorada");
                return;
            } catch (ConflitoConcorrenciaException e) {
                if (tentativa == MAXIMO_TENTATIVAS) {
                    throw e;
                }
                log.atWarn().addKeyValue("event", "conflito_concorrencia")
                        .addKeyValue("tipo", envelope.type())
                        .addKeyValue("tentativa", tentativa)
                        .log("Conflito de versao ou estoque; o handler sera repetido");
            }
        }
    }
}
