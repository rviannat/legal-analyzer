package com.rafaelvianna.legalanalyzer.datajud;
import java.time.Instant;
import java.util.List;
public record DataJudInfo(DataJudStatus status,String numeroProcesso,String tribunal,String endpoint,boolean encontrado,Integer quantidadeMovimentos,String ultimaMovimentacao,String classeProcessual,String orgaoJulgador,String grau,String mensagem,Instant consultadoEm,List<DataJudMovimento> movimentos){
 public DataJudInfo(DataJudStatus s,String n,String t,String e,boolean f,Integer q,String u,String c,String o,String g,String m,Instant i){this(s,n,t,e,f,q,u,c,o,g,m,i,List.of());}
 public DataJudInfo { movimentos=movimentos==null?List.of():List.copyOf(movimentos); }
 public static DataJudInfo aguardando(String n){return new DataJudInfo(DataJudStatus.AGUARDANDO,n,null,null,false,null,null,null,null,null,"Consulta DataJud será executada em paralelo à análise do documento.",null,List.of());}
 public static DataJudInfo naoConfigurado(){return new DataJudInfo(DataJudStatus.NAO_CONFIGURADO,"não identificado",null,null,false,null,null,null,null,null,"Integração DataJud não configurada.",null,List.of());}
 public static DataJudInfo numeroNaoIdentificado(){return new DataJudInfo(DataJudStatus.NUMERO_NAO_IDENTIFICADO,"não identificado",null,null,false,null,null,null,null,null,"Não foi possível identificar uma numeração CNJ no documento.",Instant.now(),List.of());}
}
