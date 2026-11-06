package br.com.fiap.soat.mecanica.execucao.config.observability;

import br.com.fiap.soat.mecanica.execucao.domain.ordem.StatusOrdem;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/** Eventos de negocio do Execucao em log estruturado (nos moldes do OS). */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BusinessEventLogger {

    /** {@code de} e nulo na abertura; {@code motivo} pode ser nulo. */
    public static void ordemStatus(UUID osId, StatusOrdem de, StatusOrdem para, String motivo) {
        log.atInfo()
                .addKeyValue("event", "execucao_ordem_status")
                .addKeyValue("ordemServicoId", osId)
                .addKeyValue("de", de)
                .addKeyValue("para", para)
                .addKeyValue("motivo", motivo)
                .log("Status da ordem alterado");
    }

    /** {@code resultado}: {@code RESERVADA}, {@code RECUSADA} ou {@code LIBERADA}. */
    public static void reservaPecas(UUID osId, String resultado, int pecas, String faltantes) {
        log.atInfo()
                .addKeyValue("event", "reserva_pecas")
                .addKeyValue("ordemServicoId", osId)
                .addKeyValue("resultado", resultado)
                .addKeyValue("pecas", pecas)
                .addKeyValue("faltantes", faltantes)
                .log("Reserva de pecas");
    }

    public static void comandoReemitido(UUID osId, String tipoComando, String tipoResposta) {
        log.atInfo()
                .addKeyValue("event", "comando_reemitido")
                .addKeyValue("ordemServicoId", osId)
                .addKeyValue("comando", tipoComando)
                .addKeyValue("resposta", tipoResposta)
                .log("Resposta reemitida para um reenvio do comando");
    }

    public static void comandoForaDeEstado(UUID osId, String tipoComando, StatusOrdem status) {
        log.atWarn()
                .addKeyValue("event", "comando_fora_de_estado")
                .addKeyValue("ordemServicoId", osId)
                .addKeyValue("comando", tipoComando)
                .addKeyValue("status", status)
                .log("Comando ignorado: a ordem nao esta no estado esperado");
    }
}
