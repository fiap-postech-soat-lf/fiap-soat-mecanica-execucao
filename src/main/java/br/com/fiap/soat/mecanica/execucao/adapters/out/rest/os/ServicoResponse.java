package br.com.fiap.soat.mecanica.execucao.adapters.out.rest.os;

import java.util.UUID;

/** Resposta de {@code GET /servicos/{id}} do OS (so o que o Execucao le; campos extras sao ignorados). */
record ServicoResponse(UUID id, String status, String nome, String descricao) {
}
