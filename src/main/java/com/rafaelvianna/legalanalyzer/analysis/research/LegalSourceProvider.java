package com.rafaelvianna.legalanalyzer.analysis.research;

import java.util.List;

/**
 * Recupera conteúdo de legislação/jurisprudência exclusivamente de fontes
 * autorizadas e rastreáveis (allowlist de domínios configurada em
 * {@code legal-analyzer.legal-research}).
 *
 * Implementações NUNCA devem devolver conteúdo gerado por modelo: apenas
 * texto realmente baixado de uma URL verificável.
 */
public interface LegalSourceProvider {

    /** Indica se a pesquisa está habilitada e há fontes autorizadas configuradas. */
    boolean habilitado();

    /** Nomes das fontes autorizadas configuradas (para exibir ao usuário). */
    List<String> fontesConfiguradas();

    /** Busca a consulta nas fontes autorizadas e devolve os trechos recuperados. */
    List<TrechoFonte> buscar(String consulta);
}
