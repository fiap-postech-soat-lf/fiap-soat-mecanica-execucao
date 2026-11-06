package br.com.fiap.soat.mecanica.execucao.application.ordem.port;

import java.util.UUID;

/** Servico do catalogo do OS (so o que o Execucao usa). */
public record ServicoCatalogo(UUID id, String nome, boolean ativo) {
}
