package br.com.fiap.soat.mecanica.execucao.adapters.in.web.peca.mapper;

import br.com.fiap.soat.mecanica.execucao.adapters.in.web.peca.dto.PecaResponse;
import br.com.fiap.soat.mecanica.execucao.domain.peca.Peca;

public final class PecaResponseMapper {

    private PecaResponseMapper() {
    }

    public static PecaResponse toResponse(Peca p) {
        return new PecaResponse(p.getId(), p.getStatus(), p.getNome(), p.getMarca(), p.getValorUnitario(),
                p.getQtdEstoque(), p.getQtdReservada(), p.getQtdDisponivel());
    }
}
