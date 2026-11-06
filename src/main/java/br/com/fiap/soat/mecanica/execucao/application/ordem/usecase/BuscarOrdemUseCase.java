package br.com.fiap.soat.mecanica.execucao.application.ordem.usecase;

import br.com.fiap.soat.mecanica.execucao.application.ordem.dto.OrdemDetalhe;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BuscarOrdemUseCase {

    private final OrdemSupport ordens;
    private final ReservaRepository reservas;

    public OrdemDetalhe executar(UUID osId) {
        return new OrdemDetalhe(ordens.buscar(osId), reservas.buscarPorOs(osId));
    }
}
