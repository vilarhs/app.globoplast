package br.com.globoplast.oee.service;

import br.com.globoplast.oee.model.LaunchRecord;
import br.com.globoplast.oee.util.Norm;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class OeeCalculator {
    public void recalculate(List<LaunchRecord> rows) {
        Map<String,List<LaunchRecord>> groups=new LinkedHashMap<>();
        for(LaunchRecord r:rows){
            int processed=Math.max(0,r.getTotalProduced())+Math.max(0,r.getScrapTotalPcs());
            r.setScrapPct(processed>0?Norm.round(r.getScrapTotalPcs()*100.0/processed,2):0.0);
            String key=String.valueOf(r.getDate())+"|"+r.getMachine();groups.computeIfAbsent(key,k->new ArrayList<>()).add(r);
        }
        for(List<LaunchRecord> g:groups.values())recalculateGroup(g);
    }

    private void recalculateGroup(List<LaunchRecord> g){
        // Regra operacional: uma máquina dispõe de uma única janela de 24 horas
        // por dia produtivo. A quantidade de OPs/lançamentos nunca multiplica
        // horas nem capacidade; todos recebem o mesmo indicador consolidado.
        double scheduled=24.0;
        int capacity=g.stream().mapToInt(LaunchRecord::getCapacity24h).max().orElse(0);
        double proportional=capacity>0?capacity:0;
        long good=g.stream().mapToLong(r->Math.max(0,r.getTotalProduced())).sum();
        long scrap=g.stream().mapToLong(r->Math.max(0,r.getScrapTotalPcs())).sum();
        long processed=good+scrap;
        double setup=g.stream().mapToDouble(r->Math.max(0,r.getSetupHours())).sum();
        double breakdown=g.stream().mapToDouble(r->Math.max(0,r.getBreakdownHours())).sum();
        double producing=Math.max(0,scheduled-setup-breakdown);
        double availability=scheduled>0?producing/scheduled*100.0:100.0;
        // As peças rejeitadas já penalizam a Qualidade. O Desempenho usa
        // somente as peças boas para que o Refugo não eleve este indicador e
        // anule matematicamente sua própria perda no OEE final.
        double performance=proportional>0?good/proportional*100.0:0.0;
        double quality=processed>0?good*100.0/processed:0.0;
        double oee=availability/100.0*performance/100.0*quality/100.0*100.0;
        for(LaunchRecord r:g){r.setScheduledHours(24.0);r.setCapacity24h(capacity);r.setProducingHours(Norm.round(producing,2));r.setAvailabilityPct(Norm.round(availability,2));r.setPerformancePct(Norm.round(performance,2));r.setQualityPct(Norm.round(quality,2));r.setOeePct(Norm.round(oee,2));}
    }
}
