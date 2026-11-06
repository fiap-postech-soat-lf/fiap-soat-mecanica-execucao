package br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem;

import br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto.AlocacaoIncluirRequest;
import br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto.AlocacaoResponse;
import br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto.OrdemResponse;
import br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto.PrestacaoIncluirRequest;
import br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto.PrestacaoResponse;
import br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.mapper.OrdemResponseMapper;
import br.com.fiap.soat.mecanica.execucao.application.ordem.dto.OrdemDetalhe;
import br.com.fiap.soat.mecanica.execucao.application.ordem.usecase.AlocarPecaUseCase;
import br.com.fiap.soat.mecanica.execucao.application.ordem.usecase.BuscarOrdemUseCase;
import br.com.fiap.soat.mecanica.execucao.application.ordem.usecase.ConcluirDiagnosticoUseCase;
import br.com.fiap.soat.mecanica.execucao.application.ordem.usecase.FinalizarPrestacaoUseCase;
import br.com.fiap.soat.mecanica.execucao.application.ordem.usecase.InativarAlocacaoUseCase;
import br.com.fiap.soat.mecanica.execucao.application.ordem.usecase.InativarPrestacaoUseCase;
import br.com.fiap.soat.mecanica.execucao.application.ordem.usecase.IncluirPrestacaoUseCase;
import br.com.fiap.soat.mecanica.execucao.application.ordem.usecase.ReabrirDiagnosticoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Diagnostico da ordem: prestacoes, pecas alocadas e conclusao (tabela 3.2 da F5; rotas aninhadas pelo osId, F5-c). */
@RestController
@RequestMapping("/execucoes/{osId}")
@RequiredArgsConstructor
public class OrdemController {

    private final BuscarOrdemUseCase buscarOrdemUseCase;
    private final IncluirPrestacaoUseCase incluirPrestacaoUseCase;
    private final InativarPrestacaoUseCase inativarPrestacaoUseCase;
    private final AlocarPecaUseCase alocarPecaUseCase;
    private final InativarAlocacaoUseCase inativarAlocacaoUseCase;
    private final ConcluirDiagnosticoUseCase concluirDiagnosticoUseCase;
    private final ReabrirDiagnosticoUseCase reabrirDiagnosticoUseCase;
    private final FinalizarPrestacaoUseCase finalizarPrestacaoUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('MECANICO','ALMOXARIFE')")
    @Operation(summary = "Buscar a ordem, com prestações, alocações e reservas")
    public ResponseEntity<OrdemResponse> buscar(@PathVariable UUID osId) {
        OrdemDetalhe detalhe = buscarOrdemUseCase.executar(osId);
        return ResponseEntity.ok(OrdemResponseMapper.toResponse(detalhe.ordem(), detalhe.reservas()));
    }

    @PostMapping("/prestacoes")
    @PreAuthorize("hasRole('MECANICO')")
    @Operation(summary = "Incluir uma prestação de serviço (valida o serviço no catálogo do OS)")
    public ResponseEntity<PrestacaoResponse> incluirPrestacao(@PathVariable UUID osId,
                                                              @Valid @RequestBody PrestacaoIncluirRequest request) {
        var prestacao = incluirPrestacaoUseCase.executar(osId, request.servicoId(), request.precoMaoDeObra());
        return ResponseEntity.status(HttpStatus.CREATED).body(OrdemResponseMapper.toResponse(prestacao));
    }

    @PatchMapping("/prestacoes/{id}/inativar")
    @PreAuthorize("hasRole('MECANICO')")
    @Operation(summary = "Inativar uma prestação de serviço e as peças dela")
    public ResponseEntity<PrestacaoResponse> inativarPrestacao(@PathVariable UUID osId, @PathVariable UUID id) {
        return ResponseEntity.ok(OrdemResponseMapper.toResponse(inativarPrestacaoUseCase.executar(osId, id)));
    }

    @PostMapping("/prestacoes/{id}/pecas")
    @PreAuthorize("hasRole('ALMOXARIFE')")
    @Operation(summary = "Alocar uma peça a uma prestação (não mexe no estoque; a reserva vem da Saga)")
    public ResponseEntity<AlocacaoResponse> alocarPeca(@PathVariable UUID osId, @PathVariable UUID id,
                                                       @Valid @RequestBody AlocacaoIncluirRequest request) {
        var alocacao = alocarPecaUseCase.executar(osId, id, request.pecaId(), request.quantidade());
        return ResponseEntity.status(HttpStatus.CREATED).body(OrdemResponseMapper.toResponse(alocacao));
    }

    @PatchMapping("/alocacoes/{id}/inativar")
    @PreAuthorize("hasRole('ALMOXARIFE')")
    @Operation(summary = "Inativar uma peça alocada")
    public ResponseEntity<AlocacaoResponse> inativarAlocacao(@PathVariable UUID osId, @PathVariable UUID id) {
        return ResponseEntity.ok(OrdemResponseMapper.toResponse(inativarAlocacaoUseCase.executar(osId, id)));
    }

    @PatchMapping("/prestacoes/{id}/finalizar")
    @PreAuthorize("hasRole('MECANICO')")
    @Operation(summary = "Finalizar uma prestação em execução (a última finaliza a ordem e emite ExecucaoFinalizada)")
    public ResponseEntity<OrdemResponse> finalizarPrestacao(@PathVariable UUID osId, @PathVariable UUID id) {
        return ResponseEntity.ok(OrdemResponseMapper.toResponse(finalizarPrestacaoUseCase.executar(osId, id)));
    }

    @PatchMapping("/diagnostico/concluir")
    @PreAuthorize("hasRole('MECANICO')")
    @Operation(summary = "Concluir o diagnóstico (emite DiagnosticoConcluido)")
    public ResponseEntity<OrdemResponse> concluirDiagnostico(@PathVariable UUID osId) {
        return ResponseEntity.ok(OrdemResponseMapper.toResponse(concluirDiagnosticoUseCase.executar(osId)));
    }

    @PatchMapping("/diagnostico/reabrir")
    @PreAuthorize("hasRole('MECANICO')")
    @Operation(summary = "Reabrir o diagnóstico (só em DIAGNOSTICO_CONCLUIDO sem reserva ativa)")
    public ResponseEntity<OrdemResponse> reabrirDiagnostico(@PathVariable UUID osId) {
        return ResponseEntity.ok(OrdemResponseMapper.toResponse(reabrirDiagnosticoUseCase.executar(osId)));
    }
}
