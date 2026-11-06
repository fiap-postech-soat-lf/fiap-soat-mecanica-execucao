package br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem;

import br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto.FilaResponse;
import br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto.TempoMedioResponse;
import br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.mapper.OrdemResponseMapper;
import br.com.fiap.soat.mecanica.execucao.application.ordem.usecase.ConsultarTempoMedioUseCase;
import br.com.fiap.soat.mecanica.execucao.application.ordem.usecase.ListarFilaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Consultas de execucao que nao sao de uma ordem so: fila e tempo medio (tabela 3.2 da F5). */
@RestController
@RequestMapping("/execucoes")
@RequiredArgsConstructor
public class ExecucaoController {

    private final ListarFilaUseCase listarFilaUseCase;
    private final ConsultarTempoMedioUseCase consultarTempoMedioUseCase;

    @GetMapping("/fila")
    @PreAuthorize("hasRole('MECANICO')")
    @Operation(summary = "Fila de execução, por prioridade (EM_EXECUCAO, DIAGNOSTICO_CONCLUIDO, EM_DIAGNOSTICO) e data")
    public ResponseEntity<FilaResponse> fila(
            @RequestParam(defaultValue = "" + ListarFilaUseCase.TAMANHO_PADRAO) int size,
            @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok(OrdemResponseMapper.toResponse(listarFilaUseCase.executar(size, cursor)));
    }

    @GetMapping("/tempo-medio")
    @PreAuthorize("hasRole('MECANICO')")
    @Operation(summary = "Tempo médio por serviço, em minutos (todos, ou só o servicoId informado)")
    public ResponseEntity<List<TempoMedioResponse>> tempoMedio(@RequestParam(required = false) UUID servicoId) {
        return ResponseEntity.ok(consultarTempoMedioUseCase.executar(servicoId).stream()
                .map(OrdemResponseMapper::toResponse).toList());
    }
}
