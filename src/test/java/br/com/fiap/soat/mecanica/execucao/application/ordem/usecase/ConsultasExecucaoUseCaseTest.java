package br.com.fiap.soat.mecanica.execucao.application.ordem.usecase;

import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemRepository;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.PaginaFila;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.TempoMedio;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.TempoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultasExecucaoUseCaseTest {

    @Mock
    private OrdemRepository ordens;
    @Mock
    private TempoRepository tempos;

    @Test
    void filaAjustaOTamanhoAoIntervaloDe1A100() {
        ListarFilaUseCase useCase = new ListarFilaUseCase(ordens);
        PaginaFila pagina = new PaginaFila(List.of(), null);
        when(ordens.listarFila(1, null)).thenReturn(pagina);
        when(ordens.listarFila(100, "abc")).thenReturn(pagina);
        when(ordens.listarFila(20, null)).thenReturn(pagina);

        assertThat(useCase.executar(0, null)).isSameAs(pagina);
        assertThat(useCase.executar(-5, null)).isSameAs(pagina);
        assertThat(useCase.executar(500, "abc")).isSameAs(pagina);
        assertThat(useCase.executar(ListarFilaUseCase.TAMANHO_PADRAO, null)).isSameAs(pagina);
    }

    @Test
    void tempoMedioSemServicoListaTodos() {
        TempoMedio tempo = new TempoMedio(UUID.randomUUID(), "Oleo", 90, 2);
        when(tempos.listar()).thenReturn(List.of(tempo));

        assertThat(new ConsultarTempoMedioUseCase(tempos).executar(null)).containsExactly(tempo);
    }

    @Test
    void tempoMedioDeUmServicoSemAmostrasEListaVazia() {
        UUID servico = UUID.randomUUID();
        when(tempos.buscar(servico)).thenReturn(Optional.empty());

        assertThat(new ConsultarTempoMedioUseCase(tempos).executar(servico)).isEmpty();
    }

    @Test
    void tempoMedioDeUmServicoComAmostras() {
        TempoMedio tempo = new TempoMedio(UUID.randomUUID(), "Oleo", 90, 2);
        when(tempos.buscar(tempo.servicoId())).thenReturn(Optional.of(tempo));

        assertThat(new ConsultarTempoMedioUseCase(tempos).executar(tempo.servicoId())).containsExactly(tempo);
    }
}
