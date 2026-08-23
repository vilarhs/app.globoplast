package br.com.globoplast.oee.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class LaunchRecord {
    private long id;
    private boolean erp;
    private boolean manualOverride;
    private String erpKey = "";
    private List<Long> erpIds = new ArrayList<>();
    private LocalDate date;
    private String machine = "";
    private String product = "";
    private String orderNumber = "";
    private String productionDetail = "";
    private String sector = "Sem Setor";
    private double scheduledHours;
    private int capacity24h;
    private int shiftA;
    private int shiftB;
    private int shiftC;
    private int totalProduced;
    private double unitWeightG;
    private double scrapAKg;
    private double scrapBKg;
    private double scrapCKg;
    private double scrapTotalKg;
    private int scrapTotalPcs;
    private double scrapPct;
    private int changeovers;
    private double setupHours;
    private double breakdownHours;
    private double producingHours;
    private double availabilityPct;
    private double performancePct;
    private double qualityPct;
    private double oeePct;
    private String problem = "";
    private String actionTaken = "";
    private String launchTime = "";
    private String movementAt = "";
    private String operatorErp = "";
    private String descriptionErp = "";
    private String clientErp = "";
    private int launchCount = 1;
    private boolean orderProgressAvailable;
    private int orderPlannedPcs;
    private int orderLaunchedPcs;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public boolean isErp() { return erp; }
    public void setErp(boolean erp) { this.erp = erp; }
    public boolean isManualOverride() { return manualOverride; }
    public void setManualOverride(boolean manualOverride) { this.manualOverride = manualOverride; }
    public String getErpKey() { return erpKey; }
    public void setErpKey(String erpKey) { this.erpKey = erpKey == null ? "" : erpKey; }
    public List<Long> getErpIds() { return erpIds; }
    public void setErpIds(List<Long> erpIds) { this.erpIds = erpIds == null ? new ArrayList<>() : erpIds; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getMachine() { return machine; }
    public void setMachine(String machine) { this.machine = nz(machine); }
    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = nz(product); }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = nz(orderNumber); }
    public String getProductionDetail() { return productionDetail; }
    public void setProductionDetail(String productionDetail) { this.productionDetail = nz(productionDetail); }
    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector == null || sector.isBlank() ? "Sem Setor" : sector; }
    public double getScheduledHours() { return scheduledHours; }
    public void setScheduledHours(double scheduledHours) { this.scheduledHours = scheduledHours; }
    public int getCapacity24h() { return capacity24h; }
    public void setCapacity24h(int capacity24h) { this.capacity24h = capacity24h; }
    public int getShiftA() { return shiftA; }
    public void setShiftA(int shiftA) { this.shiftA = shiftA; }
    public int getShiftB() { return shiftB; }
    public void setShiftB(int shiftB) { this.shiftB = shiftB; }
    public int getShiftC() { return shiftC; }
    public void setShiftC(int shiftC) { this.shiftC = shiftC; }
    public int getTotalProduced() { return totalProduced; }
    public void setTotalProduced(int totalProduced) { this.totalProduced = totalProduced; }
    public double getUnitWeightG() { return unitWeightG; }
    public void setUnitWeightG(double unitWeightG) { this.unitWeightG = unitWeightG; }
    public double getScrapAKg() { return scrapAKg; }
    public void setScrapAKg(double scrapAKg) { this.scrapAKg = scrapAKg; }
    public double getScrapBKg() { return scrapBKg; }
    public void setScrapBKg(double scrapBKg) { this.scrapBKg = scrapBKg; }
    public double getScrapCKg() { return scrapCKg; }
    public void setScrapCKg(double scrapCKg) { this.scrapCKg = scrapCKg; }
    public double getScrapTotalKg() { return scrapTotalKg; }
    public void setScrapTotalKg(double scrapTotalKg) { this.scrapTotalKg = scrapTotalKg; }
    public int getScrapTotalPcs() { return scrapTotalPcs; }
    public void setScrapTotalPcs(int scrapTotalPcs) { this.scrapTotalPcs = scrapTotalPcs; }
    public double getScrapPct() { return scrapPct; }
    public void setScrapPct(double scrapPct) { this.scrapPct = scrapPct; }
    public int getChangeovers() { return changeovers; }
    public void setChangeovers(int changeovers) { this.changeovers = changeovers; }
    public double getSetupHours() { return setupHours; }
    public void setSetupHours(double setupHours) { this.setupHours = setupHours; }
    public double getBreakdownHours() { return breakdownHours; }
    public void setBreakdownHours(double breakdownHours) { this.breakdownHours = breakdownHours; }
    public double getProducingHours() { return producingHours; }
    public void setProducingHours(double producingHours) { this.producingHours = producingHours; }
    public double getAvailabilityPct() { return availabilityPct; }
    public void setAvailabilityPct(double availabilityPct) { this.availabilityPct = availabilityPct; }
    public double getPerformancePct() { return performancePct; }
    public void setPerformancePct(double performancePct) { this.performancePct = performancePct; }
    public double getQualityPct() { return qualityPct; }
    public void setQualityPct(double qualityPct) { this.qualityPct = qualityPct; }
    public double getOeePct() { return oeePct; }
    public void setOeePct(double oeePct) { this.oeePct = oeePct; }
    public String getProblem() { return problem; }
    public void setProblem(String problem) { this.problem = nz(problem); }
    public String getActionTaken() { return actionTaken; }
    public void setActionTaken(String actionTaken) { this.actionTaken = nz(actionTaken); }
    public String getLaunchTime() { return launchTime; }
    public void setLaunchTime(String launchTime) { this.launchTime = nz(launchTime); }
    public String getMovementAt() { return movementAt; }
    public void setMovementAt(String movementAt) { this.movementAt = nz(movementAt); }
    public String getOperatorErp() { return operatorErp; }
    public void setOperatorErp(String operatorErp) { this.operatorErp = nz(operatorErp); }
    public String getDescriptionErp() { return descriptionErp; }
    public void setDescriptionErp(String descriptionErp) { this.descriptionErp = nz(descriptionErp); }
    public String getClientErp() { return clientErp; }
    public void setClientErp(String clientErp) { this.clientErp = nz(clientErp); }
    public int getLaunchCount() { return launchCount; }
    public void setLaunchCount(int launchCount) { this.launchCount = Math.max(0, launchCount); }
    public boolean isOrderProgressAvailable() { return orderProgressAvailable; }
    public void setOrderProgressAvailable(boolean orderProgressAvailable) { this.orderProgressAvailable = orderProgressAvailable; }
    public int getOrderPlannedPcs() { return orderPlannedPcs; }
    public void setOrderPlannedPcs(int orderPlannedPcs) { this.orderPlannedPcs = Math.max(0, orderPlannedPcs); }
    public int getOrderLaunchedPcs() { return orderLaunchedPcs; }
    public void setOrderLaunchedPcs(int orderLaunchedPcs) { this.orderLaunchedPcs = Math.max(0, orderLaunchedPcs); }
    public int getOrderRemainingPcs() { return Math.max(0, orderPlannedPcs - orderLaunchedPcs); }

    public int processedPcs() { return Math.max(0, totalProduced) + Math.max(0, scrapTotalPcs); }
    public String identityKey() {
        return String.valueOf(date) + "|" + orderNumber.trim().toUpperCase() + "|" +
                machine.trim().toUpperCase() + "|" + product.replace(" ", "").trim().toUpperCase();
    }
    public LaunchRecord copy() {
        LaunchRecord x = new LaunchRecord();
        x.id=id; x.erp=erp; x.manualOverride=manualOverride; x.erpKey=erpKey; x.erpIds=new ArrayList<>(erpIds);
        x.date=date; x.machine=machine; x.product=product; x.orderNumber=orderNumber; x.productionDetail=productionDetail; x.sector=sector;
        x.scheduledHours=scheduledHours; x.capacity24h=capacity24h; x.shiftA=shiftA; x.shiftB=shiftB; x.shiftC=shiftC; x.totalProduced=totalProduced;
        x.unitWeightG=unitWeightG; x.scrapAKg=scrapAKg; x.scrapBKg=scrapBKg; x.scrapCKg=scrapCKg; x.scrapTotalKg=scrapTotalKg; x.scrapTotalPcs=scrapTotalPcs;
        x.scrapPct=scrapPct; x.changeovers=changeovers; x.setupHours=setupHours; x.breakdownHours=breakdownHours; x.producingHours=producingHours;
        x.availabilityPct=availabilityPct; x.performancePct=performancePct; x.qualityPct=qualityPct; x.oeePct=oeePct;
        x.problem=problem; x.actionTaken=actionTaken; x.launchTime=launchTime; x.movementAt=movementAt; x.operatorErp=operatorErp; x.descriptionErp=descriptionErp; x.clientErp=clientErp; x.launchCount=launchCount;
        x.orderProgressAvailable=orderProgressAvailable; x.orderPlannedPcs=orderPlannedPcs; x.orderLaunchedPcs=orderLaunchedPcs;
        return x;
    }
    private static String nz(String v) { return v == null ? "" : v; }
}
