package br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.mapper;

import br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto.AlocacaoResponse;
import br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto.FilaResponse;
import br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto.OrdemResponse;
import br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto.PrestacaoResponse;
import br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto.ReservaResponse;
import br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto.TempoMedioResponse;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Alocacao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.PaginaFila;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Prestacao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Reserva;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.TempoMedio;

import java.util.List;

public final class OrdemResponseMapper {

    private OrdemResponseMapper() {
    }

    public static OrdemResponse toResponse(OrdemExecucao o) {
        return toResponse(o, null);
    }

    public static OrdemResponse toResponse(OrdemExecucao o, List<Reserva> reservas) {
        return new OrdemResponse(o.getOsId(), o.getSagaId(), o.getVeiculoId(), o.getPlaca(), o.getStatus(),
                o.getAbertaEm(), o.getDiagnosticoConcluidoEm(), o.getIniciadaEm(), o.getFinalizadaEm(),
                o.getMotivo(), o.getPrestacoes().stream().map(OrdemResponseMapper::toResponse).toList(),
                reservas == null ? null : reservas.stream().map(OrdemResponseMapper::toResponse).toList());
    }

    public static PrestacaoResponse toResponse(Prestacao p) {
        return new PrestacaoResponse(p.getId(), p.getStatus(), p.getServicoId(), p.getNomeServico(),
                p.getPrecoMaoDeObra(), p.getDataInicio(), p.getDataFim(),
                p.getAlocacoes().stream().map(OrdemResponseMapper::toResponse).toList());
    }

    public static AlocacaoResponse toResponse(Alocacao a) {
        return new AlocacaoResponse(a.getId(), a.getStatus(), a.getPecaId(), a.getDescricao(), a.getQuantidade(),
                a.getValorUnitario());
    }

    public static FilaResponse toResponse(PaginaFila pagina) {
        return new FilaResponse(pagina.ordens().stream()
                .map(o -> new FilaResponse.Item(o.getOsId(), o.getPlaca(), o.getStatus(), o.getAbertaEm(),
                        o.prestacoesAtivas().size()))
                .toList(), pagina.proximoCursor());
    }

    public static TempoMedioResponse toResponse(TempoMedio t) {
        return new TempoMedioResponse(t.servicoId(), t.nomeServico(), t.minutosMedio(), t.amostras());
    }

    private static ReservaResponse toResponse(Reserva r) {
        return new ReservaResponse(r.pecaId(), r.quantidade(), r.status(), r.reservadaEm(), r.atualizadaEm());
    }
}
