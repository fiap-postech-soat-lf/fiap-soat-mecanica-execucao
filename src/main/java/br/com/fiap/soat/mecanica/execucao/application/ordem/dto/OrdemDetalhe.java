package br.com.fiap.soat.mecanica.execucao.application.ordem.dto;

import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Reserva;

import java.util.List;

/** A ordem com as linhas de reserva dela (GET /execucoes/{osId}). */
public record OrdemDetalhe(OrdemExecucao ordem, List<Reserva> reservas) {
}
