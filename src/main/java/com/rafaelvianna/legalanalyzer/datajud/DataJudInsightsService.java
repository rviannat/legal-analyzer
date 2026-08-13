package com.rafaelvianna.legalanalyzer.datajud;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rafaelvianna.legalanalyzer.config.AppProperties;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
@Service
public class DataJudInsightsService {
 private final AppProperties p; private final ObjectMapper m; private final HttpClient h;
 public DataJudInsightsService(AppProperties p,ObjectMapper m){this.p=p;this.m=m;this.h=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(p.dataJud().timeoutSecondsOuPadrao())).build();}
 public DataJudInsights analisar(DataJudInfo i){
  if(i==null||i.status()!=DataJudStatus.ENCONTRADO)return DataJudInsights.indisponivel(i==null?DataJudInfo.numeroNaoIdentificado():i,"Processo DataJud ainda não disponível.");
  Instant inicio=i.movimentos().stream().map(DataJudMovimento::dataInstant).filter(x->x!=null).min(Instant::compareTo).orElse(null);
  Integer idade=inicio==null?null:Math.max(0,(int)ChronoUnit.DAYS.between(inicio,Instant.now()));
  if(!p.dataJud().estatisticasConfiguradas())return parcial(i,idade,"Idade estimada pela primeira movimentação pública. Fonte agregada oficial não configurada.");
  try{
   String body=m.createObjectNode().put("tribunal",s(i.tribunal())).put("classe",s(i.classeProcessual())).put("orgao",s(i.orgaoJulgador())).toString();
   HttpRequest q=HttpRequest.newBuilder(URI.create(p.dataJud().estatisticasUrl())).timeout(Duration.ofSeconds(p.dataJud().timeoutSecondsOuPadrao())).header("Authorization","APIKey "+p.dataJud().apiKey()).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
   HttpResponse<String> r=h.send(q,HttpResponse.BodyHandlers.ofString()); if(r.statusCode()<200||r.statusCode()>=300)throw new IllegalStateException("HTTP "+r.statusCode());
   JsonNode n=m.readTree(r.body()); Integer media=n.hasNonNull("duracaoMediaDias")?n.get("duracaoMediaDias").asInt():null; Double acordo=n.hasNonNull("probabilidadeAcordo")?n.get("probabilidadeAcordo").asDouble():null; Double pericia=n.hasNonNull("probabilidadePericia")?n.get("probabilidadePericia").asDouble():null; Double cong=n.hasNonNull("congestionamento")?n.get("congestionamento").asDouble():null;
   String nivel=n.path("nivelCongestionamento").asText(null),fonte=n.path("fonte").asText(p.dataJud().estatisticasUrl()); Integer pct=media==null||idade==null||media==0?null:Math.min(999,Math.round(idade*100f/media));
   return new DataJudInsights(DataJudInsights.Status.DISPONIVEL,i.tribunal(),null,i.classeProcessual(),null,i.orgaoJulgador(),idade,media,pct,acordo,pericia,cong,nivel,fonte,"Insights obtidos da fonte agregada configurada.",Instant.now());
  }catch(Exception e){return parcial(i,idade,"Fonte estatística indisponível; exibindo apenas a idade observável.");}
 }
 private DataJudInsights parcial(DataJudInfo i,Integer idade,String msg){return new DataJudInsights(DataJudInsights.Status.PARCIAL,i.tribunal(),null,i.classeProcessual(),null,i.orgaoJulgador(),idade,null,null,null,null,null,null,null,msg,Instant.now());}
 private static String s(String x){return x==null?"":x;}
}
