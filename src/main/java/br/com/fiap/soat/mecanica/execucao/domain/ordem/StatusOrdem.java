package br.com.fiap.soat.mecanica.execucao.domain.ordem;

/** Estados da ordem no Execucao (tabela 7.1 da F5). */
public enum StatusOrdem {

    EM_DIAGNOSTICO(3),
    DIAGNOSTICO_CONCLUIDO(2),
    EM_EXECUCAO(1),
    FINALIZADA(0),
    CANCELADA(0);

    private final int prioridadeFila;

    StatusOrdem(int prioridadeFila) {
        this.prioridadeFila = prioridadeFila;
    }

    /** Prioridade na fila de execucao (1 e a mais urgente); 0 quando a ordem nao esta mais na fila (F5-j). */
    public int prioridadeFila() {
        return prioridadeFila;
    }

    public boolean naFila() {
        return prioridadeFila > 0;
    }
}
