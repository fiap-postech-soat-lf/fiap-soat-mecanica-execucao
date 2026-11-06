package br.com.fiap.soat.mecanica.execucao.domain.ordem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TempoRepository {

    /**
     * Registra na transacao aberta o {@code ADD} de uma amostra ({@code minutos}) no servico; o nome e gravado na
     * primeira vez. Sem conferencia de versao: quem protege contra contagem dupla e a versao da ordem, na mesma transacao.
     */
    void acumular(UUID servicoId, String nomeServico, long minutos);

    Optional<TempoMedio> buscar(UUID servicoId);

    /** Todos os servicos com amostras (um item por servico do catalogo: dezenas de itens). */
    List<TempoMedio> listar();
}
