package br.com.fiap.soat.mecanica.execucao.application.ordem.port;

import java.util.UUID;

public interface CatalogoServicosPort {

    /**
     * @throws br.com.fiap.soat.mecanica.execucao.application.ordem.exception.ServicoInvalidoException    servico inexistente ou inativo
     * @throws br.com.fiap.soat.mecanica.execucao.application.ordem.exception.OsIndisponivelException     OS fora do ar depois dos retries
     * @throws br.com.fiap.soat.mecanica.execucao.application.ordem.exception.IntegracaoOsException       OS recusou o token repassado
     */
    ServicoCatalogo consultar(UUID servicoId);
}
