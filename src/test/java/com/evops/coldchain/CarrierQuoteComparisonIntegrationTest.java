package com.evops.coldchain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.evops.coldchain.dto.DispatchRequest;
import com.evops.coldchain.dto.DrugCreateRequest;
import com.evops.coldchain.dto.OrderCreateRequest;
import com.evops.coldchain.dto.VehicleCreateRequest;
import com.evops.coldchain.entity.PriceLock;
import com.evops.coldchain.entity.TransportBatch;
import com.evops.coldchain.mapper.PriceLockMapper;
import com.evops.coldchain.service.DrugService;
import com.evops.coldchain.service.OrderService;
import com.evops.coldchain.service.TransportBatchService;
import com.evops.coldchain.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 冷链承运商报价比选（V8）验收测试。
 *
 * 覆盖：多承运商并行报价；金额拆基础运费/附加费/税率且税额总价服务端重算；同承运商有效报价唯一、
 * 撤报留行可重报；锁价唯一中选（中选 WON/其余 LOST、批次 CLOSED、报价快照保存）；
 * 过期报价不能锁价/被新批次引用（扫描幂等推进 EXPIRED、批次流标 EXPIRED）；
 * 人工改价必须两步审批、审批中拦截派车、同意后执行价生效但原始报价快照保留、驳回恢复原价；
 * 派车消费锁价单并回填批次；从锁价单/运输批次反查原报价单、报价提交人、锁价处理人、异常原因与处理人、改价链；
 * Shiro 分权（carrier 报价、dispatcher 询价锁价、qa 审批，越权 403）。
 *
 * 随机端口真实启服务，HTTP Basic 完整生效；不回滚，验证真实持久化。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CarrierQuoteComparisonIntegrationTest {

    private static final ObjectMapper M = new ObjectMapper();
    private static final String BASE = "/api/cold-chain";

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate http;
    @Autowired private DrugService drugService;
    @Autowired private OrderService orderService;
    @Autowired private VehicleService vehicleService;
    @Autowired private TransportBatchService batchService;
    @Autowired private PriceLockMapper priceLockMapper;

    // ====================================================================
    // 1) 并行报价 + 金额拆分服务端重算 + 撤报留行可重报 + 锁价唯一中选与快照
    // ====================================================================
    @Test
    void parallelQuotes_amountBreakdown_lockUniqueWinnerWithSnapshot() throws Exception {
        long boxId = createBoxType();
        long batchId = createQuoteBatch(boxId, LocalDateTime.now().plusDays(1));
        long carrierA = createCarrier("华雪冷链");
        long carrierB = createCarrier("顺冷物流");
        long carrierC = createCarrier("极速冷运");

        // A：基础 1000 + 附加费（燃油60+装卸40）+ 税率9% => 附加费100、税额99、含税总价1199
        long quoteA = submitQuote(batchId, carrierA, "1000.00", "0.09",
                Arrays.asList(surcharge("FUEL", "温控燃油附加", "60.00"),
                        surcharge("LOAD", "装卸费", "40.00")),
                LocalDateTime.now().plusDays(2));
        JsonNode a = quoteDetail(quoteA);
        assertMoney(a.path("surchargeTotal"), "100.00");
        assertMoney(a.path("taxAmount"), "99.00");
        assertMoney(a.path("totalAmount"), "1199.00");

        // B：基础 1050 无附加费 9% => 税额94.50、总价1144.50（最低）
        long quoteB = submitQuote(batchId, carrierB, "1050.00", "0.09", null,
                LocalDateTime.now().plusDays(2));
        assertMoney(quoteDetail(quoteB).path("totalAmount"), "1144.50");

        // C：基础 900 + 附加费200 税率6% => 税基1100、税额66、总价1166
        long quoteC = submitQuote(batchId, carrierC, "900.00", "0.06",
                Arrays.asList(surcharge("INSURE", "货运保险", "200.00")),
                LocalDateTime.now().plusDays(2));
        assertMoney(quoteDetail(quoteC).path("taxAmount"), "66.00");
        assertMoney(quoteDetail(quoteC).path("totalAmount"), "1166.00");

        // 多承运商并行报价落 3 行，比选视图按含税总价升序：B(1144.50) < C(1166) < A(1199)
        JsonNode comparison = get("/quote-batches/" + batchId + "/comparison", "dispatcher").path("data");
        assertEquals(3, comparison.path("quotes").size());
        assertMoney(comparison.path("quotes").get(0).path("quote").path("totalAmount"), "1144.50");
        // 附加费明细随报价行返回（A 两笔、B 无附加费）
        assertEquals(2, comparison.path("quotes").get(2).path("surcharges").size());
        assertEquals(0, comparison.path("quotes").get(0).path("surcharges").size());

        // 同承运商不撤报重复报价被停点拒绝
        JsonNode dup = post("/quote-batches/quotes",
                quoteBody(batchId, carrierA, "999.00", "0.09", null, LocalDateTime.now().plusDays(1)),
                "carrier");
        assertFalseSuccess(dup, "已有有效报价");

        // 撤报 A：原行置 WITHDRAWN 保留，可重新报价产生新行
        assertTrue(post("/quote-batches/quotes/" + quoteA + "/withdraw",
                map("reason", "运力临时紧张撤报"), "carrier").path("success").asBoolean());
        assertEquals(CcConstants.QUOTE_WITHDRAWN, quoteDetail(quoteA).path("status").asText());
        long quoteA2 = submitQuote(batchId, carrierA, "1200.00", "0.09", null,
                LocalDateTime.now().plusDays(2));
        assertNotEquals(quoteA, quoteA2);

        // 锁价只能中选唯一一个有效方案：中选 B
        long lockId = lockPrice(batchId, quoteB);
        PriceLock lock = priceLockMapper.selectById(lockId);
        assertEquals(CcConstants.PLOCK_LOCKED, lock.getStatus());
        // 中选报价 WON、同批其余仍有效报价 LOST（含 A 新行、C），撤报旧行保持 WITHDRAWN
        assertEquals(CcConstants.QUOTE_WON, quoteDetail(quoteB).path("status").asText());
        assertEquals(CcConstants.QUOTE_LOST, quoteDetail(quoteC).path("status").asText());
        assertEquals(CcConstants.QUOTE_LOST, quoteDetail(quoteA2).path("status").asText());
        assertEquals(CcConstants.QUOTE_WITHDRAWN, quoteDetail(quoteA).path("status").asText());

        // 锁价快照 = 中选原报价金额（orig 与 exec 初始都等于原报价）
        assertEquals(0, lock.getOrigBaseFreight().compareTo(new BigDecimal("1050.00")));
        assertEquals(0, lock.getOrigSurchargeTotal().compareTo(BigDecimal.ZERO));
        assertEquals(0, lock.getOrigTaxAmount().compareTo(new BigDecimal("94.50")));
        assertEquals(0, lock.getOrigTotalAmount().compareTo(new BigDecimal("1144.50")));
        assertEquals(0, lock.getExecTotalAmount().compareTo(lock.getOrigTotalAmount()));
        assertEquals(CcConstants.PRICE_SOURCE_QUOTE, lock.getPriceSource());
        assertTrue(lock.getCarrierNameSnap().startsWith("顺冷物流"));
        // 询价批次关闭，不能再锁第二次；落选/撤报报价也不能再被中选
        assertEquals(CcConstants.QBATCH_CLOSED, get("/quote-batches/" + batchId + "/comparison", "dispatcher")
                .path("data").path("quoteBatch").path("status").asText());
        assertFalseSuccess(lockResponse(batchId, quoteC), "只有比选中的批次可以锁价");
        assertFalseSuccess(lockResponse(batchId, quoteB), "只有比选中的批次可以锁价");
    }

    // ====================================================================
    // 2) 过期报价不能被锁价/被新批次引用；扫描幂等推进，无有效报价批次流标
    // ====================================================================
    @Test
    void expiredQuote_cannotBeReferenced_batchGoesExpired() throws Exception {
        long boxId = createBoxType();
        long carrierA = createCarrier("时效冷链");
        long carrierB = createCarrier("延时冷链");

        // 批次一：A 报价 1 秒后过期；B 正常有效
        long batch1 = createQuoteBatch(boxId, LocalDateTime.now().plusDays(1));
        long shortQuote = submitQuote(batch1, carrierA, "1000.00", "0.09", null,
                LocalDateTime.now().plusSeconds(1));
        long goodQuote = submitQuote(batch1, carrierB, "1200.00", "0.09", null,
                LocalDateTime.now().plusDays(1));
        Thread.sleep(2000L);
        // 手动触发过期扫描（与定时任务同逻辑）
        JsonNode scan = post("/quote-batches/expiry-scan", new LinkedHashMap<>(), "dispatcher");
        assertTrue(scan.path("success").asBoolean());
        assertTrue(scan.path("data").path("expiredQuotes").asInt() >= 1);
        assertEquals(CcConstants.QUOTE_EXPIRED, quoteDetail(shortQuote).path("status").asText());
        // 金额行原样保留
        assertMoney(quoteDetail(shortQuote).path("totalAmount"), "1090.00");

        // 过期报价不能中选锁价（即时判过期，停点说明明确）
        assertFalseSuccess(lockResponse(batch1, shortQuote), "过期");
        // 批次仍有 B 的有效报价，可正常锁价
        assertEquals(CcConstants.QBATCH_OPEN,
                get("/quote-batches/" + batch1 + "/comparison", "dispatcher")
                        .path("data").path("quoteBatch").path("status").asText());
        lockPrice(batch1, goodQuote);

        // 批次二：唯一报价过期且截止时间已过 -> 批次流标 EXPIRED（过期报价不能再被任何新批次引用）
        long batch2 = createQuoteBatch(boxId, LocalDateTime.now().plusSeconds(1));
        long onlyQuote = submitQuote(batch2, carrierA, "800.00", "0.09", null,
                LocalDateTime.now().plusSeconds(1));
        Thread.sleep(2000L);
        post("/quote-batches/expiry-scan", new LinkedHashMap<>(), "dispatcher");
        assertEquals(CcConstants.QUOTE_EXPIRED, quoteDetail(onlyQuote).path("status").asText());
        assertEquals(CcConstants.QBATCH_EXPIRED,
                get("/quote-batches/" + batch2 + "/comparison", "dispatcher")
                        .path("data").path("quoteBatch").path("status").asText());
        assertFalseSuccess(lockResponse(batch2, onlyQuote), "只有比选中的批次可以锁价");
    }

    // ====================================================================
    // 3) 人工改价两步审批：审批中拦截派车；同意后执行价生效、原始报价快照保留；驳回恢复原价
    // ====================================================================
    @Test
    void manualPriceChange_requiresApproval_keepsOriginalQuote() throws Exception {
        long boxId = createBoxType();
        long batchId = createQuoteBatch(boxId, LocalDateTime.now().plusDays(1));
        long carrierA = createCarrier("议价冷链甲");
        long carrierB = createCarrier("议价冷链乙");
        long q1 = submitQuote(batchId, carrierA, "1050.00", "0.09", null, LocalDateTime.now().plusDays(2));
        long q2 = submitQuote(batchId, carrierB, "1080.00", "0.09", null, LocalDateTime.now().plusDays(2));
        long lockId = lockPrice(batchId, q1);

        // 申请改价：原 1144.50 -> 基础1000 无附加费 9% => 1090.00
        long adjId = applyAdjustment(lockId, "1000.00", "0.09", null, "长期合作协议下浮");
        JsonNode adj = get("/price-adjustments/" + adjId, "qa").path("data");
        assertEquals(CcConstants.ADJUST_PENDING, adj.path("status").asText());
        assertEquals(CcConstants.PLOCK_PRICE_PENDING, priceLockMapper.selectById(lockId).getStatus());

        // 改价审批半成品：派车消费被拦截
        long orderId = newOrder();
        long vehicleId = newVehicle();
        DispatchRequest dr = new DispatchRequest();
        dr.setOrderId(orderId);
        dr.setVehicleId(vehicleId);
        dr.setPriceLockId(lockId);
        expectBusiness(() -> batchService.dispatch(dr), "人工改价申请尚未审批完成");

        // 越权：carrier（报价方）不能审批改价 -> 403
        ResponseEntity<String> forbidden = rawPost("/price-adjustments/" + adjId + "/approve",
                map("reviewNote", "同意"), "carrier", "carrier123");
        assertEquals(403, forbidden.getStatusCode().value());

        // qa 同意：执行价变为 1090，价格来源 ADJUSTED，但原始报价快照仍是 1144.50
        assertTrue(post("/price-adjustments/" + adjId + "/approve",
                map("reviewNote", "协议价属实，同意执行"), "qa").path("success").asBoolean());
        PriceLock after = priceLockMapper.selectById(lockId);
        assertEquals(CcConstants.PLOCK_LOCKED, after.getStatus());
        assertEquals(0, after.getExecTotalAmount().compareTo(new BigDecimal("1090.00")));
        assertEquals(0, after.getOrigTotalAmount().compareTo(new BigDecimal("1144.50")));
        assertEquals(0, after.getOrigBaseFreight().compareTo(new BigDecimal("1050.00")));
        assertEquals(CcConstants.PRICE_SOURCE_ADJUSTED, after.getPriceSource());

        // 审批同意后派车消费成功，回填中选承运商与执行价
        TransportBatch batch = batchService.dispatch(dr);
        assertEquals(CcConstants.PLOCK_CONSUMED, priceLockMapper.selectById(lockId).getStatus());
        assertEquals(lockId, batch.getPriceLockId());
        assertTrue(batch.getLockedCarrierName().startsWith("议价冷链甲"));
        assertEquals(0, batch.getLockedTotalAmount().compareTo(new BigDecimal("1090.00")));
        // 一个锁价方案只能被一个运输批次消费（用第二张真实发货单再派车，仍在锁价卡口被拦截）
        long secondOrderId = newOrder();
        expectBusiness(() -> batchService.dispatch(newDispatch(secondOrderId, vehicleId, lockId)),
                "只能用于一个运输批次");

        // 驳回路径：另一个锁价单，驳回后执行价仍是原始报价、锁价单恢复 LOCKED
        long boxId2 = createBoxType();
        long batch2 = createQuoteBatch(boxId2, LocalDateTime.now().plusDays(1));
        long qa2 = submitQuote(batch2, carrierA, "2000.00", "0.09", null, LocalDateTime.now().plusDays(2));
        submitQuote(batch2, carrierB, "2100.00", "0.09", null, LocalDateTime.now().plusDays(2));
        long lock2 = lockPrice(batch2, qa2);
        long adj2 = applyAdjustment(lock2, "1500.00", "0.09", null, "临时改价测试");
        assertTrue(post("/price-adjustments/" + adj2 + "/reject",
                map("reviewNote", "缺少协议依据，驳回"), "qa").path("success").asBoolean());
        PriceLock rejected = priceLockMapper.selectById(lock2);
        assertEquals(CcConstants.PLOCK_LOCKED, rejected.getStatus());
        assertEquals(0, rejected.getExecTotalAmount().compareTo(new BigDecimal("2180.00")));
        assertEquals(CcConstants.PRICE_SOURCE_QUOTE, rejected.getPriceSource());
    }

    // ====================================================================
    // 4) 从冷链药品运输锁价单反查：原报价单、提交人/锁价人、异常原因与处理人、改价链
    // ====================================================================
    @Test
    void traceFromLockAndBatch_locatesOriginalQuoteHandlersAndExceptionReasons() throws Exception {
        long boxId = createBoxType();
        long batchId = createQuoteBatch(boxId, LocalDateTime.now().plusDays(1));
        long carrierA = createCarrier("溯源冷链");
        long carrierB = createCarrier("可比冷链");
        long quoteA = submitQuote(batchId, carrierA, "1000.00", "0.09",
                Arrays.asList(surcharge("WAIT", "等候费", "30.00")), LocalDateTime.now().plusDays(2));
        long quoteB = submitQuote(batchId, carrierB, "1300.00", "0.09", null, LocalDateTime.now().plusDays(2));

        // 比选中登记报价异常（计价争议，定位到 B 的报价），随后由 qa 闭环
        JsonNode exc = post("/quote-exceptions", map(
                "quoteBatchId", batchId,
                "quoteId", quoteB,
                "exceptionType", "PRICE_DISPUTE",
                "description", "报价附加费口径与询价单不一致，需澄清"), "dispatcher");
        assertTrue(exc.path("success").asBoolean(), exc.toString());
        long excId = exc.path("data").path("id").asLong();
        assertTrue(post("/quote-exceptions/" + excId + "/resolve",
                map("resolveNote", "承运商已澄清为含税口径，记录留档"), "qa").path("success").asBoolean());

        long lockId = lockPrice(batchId, quoteA);
        long adjId = applyAdjustment(lockId, "980.00", "0.09", null, "批量发货优惠");
        assertTrue(post("/price-adjustments/" + adjId + "/approve",
                map("reviewNote", "同意按协议价执行"), "qa").path("success").asBoolean());

        long orderId = newOrder();
        long vehicleId = newVehicle();
        DispatchRequest dr = new DispatchRequest();
        dr.setOrderId(orderId);
        dr.setVehicleId(vehicleId);
        dr.setPriceLockId(lockId);
        TransportBatch batch = batchService.dispatch(dr);

        // 从锁价单反查
        JsonNode trace = get("/price-locks/" + lockId + "/trace", "dispatcher").path("data");
        assertEquals(String.valueOf(quoteA), trace.path("wonQuote").path("id").asText());
        assertEquals("BJ", trace.path("wonQuote").path("quoteNo").asText().substring(0, 2));
        // 处理人：报价提交人=carrier 账号，锁价人=dispatcher，改价申请人=dispatcher、审批人=qa
        assertEquals("carrier", trace.path("handlers").path("quoteSubmitName").asText());
        assertEquals("dispatcher", trace.path("handlers").path("lockedByName").asText());
        // 异常原因与处理人
        JsonNode excHandlers = trace.path("handlers").path("quoteExceptions");
        assertEquals(1, excHandlers.size());
        assertEquals("PRICE_DISPUTE", excHandlers.get(0).path("exceptionType").asText());
        assertTrue(excHandlers.get(0).path("description").asText().contains("附加费口径"));
        assertEquals("qa", excHandlers.get(0).path("resolveName").asText());
        assertTrue(excHandlers.get(0).path("resolveNote").asText().contains("含税口径"));
        // 中选报价的附加费明细、改价链（申请+审批人）随链路返回
        assertEquals("等候费", trace.path("wonSurcharges").get(0).path("feeName").asText());
        JsonNode adj = trace.path("priceAdjustments").get(0).path("adjustment");
        assertEquals(CcConstants.ADJUST_APPROVED, adj.path("status").asText());
        assertEquals("dispatcher", adj.path("applyName").asText());
        assertEquals("qa", adj.path("reviewName").asText());
        // 同批次全部报价都在反查视图内（中选/落选）
        assertEquals(2, trace.path("allQuotes").size());
        assertNotNull(trace.path("opLogs"));
        assertTrue(trace.path("opLogs").size() > 0);

        // 从运输批次反查同一链路，并带回批次号
        JsonNode batchTrace = get("/batches/" + batch.getId() + "/price-trace", "dispatcher").path("data");
        assertEquals(batch.getBatchNo(), batchTrace.path("transportBatchNo").asText());
        assertEquals(String.valueOf(lockId), batchTrace.path("priceLock").path("id").asText());
        assertEquals(String.valueOf(quoteA), batchTrace.path("wonQuote").path("id").asText());
    }

    // ====================================================================
    // 5) Shiro 分权：报价方只能报价，比选/建档/审批越权 403
    // ====================================================================
    @Test
    void shiroPermissions_separateQuoteSubmitLockReview() throws Exception {
        long boxId = createBoxType();
        // carrier 不能建箱型/承运商主档
        assertEquals(403, rawPost("/box-types",
                map("boxTypeCode", "X" + System.nanoTime(), "boxTypeName", "越权箱型"),
                "carrier", "carrier123").getStatusCode().value());
        // carrier 不能发起询价
        assertEquals(403, rawPost("/quote-batches",
                map("zoneCode", "REFRIGERATE", "boxTypeId", boxId, "serviceLevel", "STANDARD"),
                "carrier", "carrier123").getStatusCode().value());

        long batchId = createQuoteBatch(boxId, LocalDateTime.now().plusDays(1));
        long carrierId = createCarrier("权限验证冷链");
        // dispatcher 不能代承运商报价
        assertEquals(403, rawPost("/quote-batches/quotes",
                quoteBody(batchId, carrierId, "1000.00", "0.09", null, LocalDateTime.now().plusDays(1)),
                "dispatcher", "dispatcher123").getStatusCode().value());
        long quoteId = submitQuote(batchId, carrierId, "1000.00", "0.09", null,
                LocalDateTime.now().plusDays(1));
        // carrier 不能锁价
        assertEquals(403, rawPost("/price-locks",
                map("quoteBatchId", batchId, "wonQuoteId", quoteId),
                "carrier", "carrier123").getStatusCode().value());
        // dispatcher 锁价成功
        assertTrue(lockResponse(batchId, quoteId).path("success").asBoolean());
    }

    // ---------------- 业务建样（HTTP，走完整 Shiro 链） ----------------

    private long createCarrier(String name) {
        JsonNode node = post("/carriers",
                map("carrierName", name + "-" + System.nanoTime(),
                        "contactName", "联系人", "qualification", "冷链运输GSP"), "admin");
        assertTrue(node.path("success").asBoolean(), node.toString());
        return node.path("data").path("id").asLong();
    }

    private long createBoxType() {
        JsonNode node = post("/box-types",
                map("boxTypeCode", "R2R8-" + System.nanoTime(),
                        "boxTypeName", "2~8℃40L冷藏箱", "volumeLiter", "40.00"), "admin");
        assertTrue(node.path("success").asBoolean(), node.toString());
        return node.path("data").path("id").asLong();
    }

    private long createQuoteBatch(long boxId, LocalDateTime deadline) {
        JsonNode node = post("/quote-batches", map(
                "zoneCode", "REFRIGERATE",
                "boxTypeId", boxId,
                "serviceLevel", "STANDARD",
                "deadline", deadline.toString(),
                "expectShipTime", LocalDateTime.now().plusDays(3).toString(),
                "cargoDesc", "HPV疫苗冷藏运输"), "dispatcher");
        assertTrue(node.path("success").asBoolean(), node.toString());
        return node.path("data").path("id").asLong();
    }

    private long submitQuote(long quoteBatchId, long carrierId, String base, String taxRate,
                             List<Map<String, Object>> surcharges, LocalDateTime validUntil) {
        JsonNode node = post("/quote-batches/quotes",
                quoteBody(quoteBatchId, carrierId, base, taxRate, surcharges, validUntil), "carrier");
        assertTrue(node.path("success").asBoolean(), node.toString());
        return node.path("data").path("id").asLong();
    }

    private Map<String, Object> quoteBody(long quoteBatchId, long carrierId, String base, String taxRate,
                                          List<Map<String, Object>> surcharges, LocalDateTime validUntil) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("quoteBatchId", quoteBatchId);
        m.put("carrierId", carrierId);
        m.put("baseFreight", base);
        m.put("taxRate", taxRate);
        if (surcharges != null) {
            m.put("surcharges", surcharges);
        }
        m.put("validUntil", validUntil.toString());
        return m;
    }

    private Map<String, Object> surcharge(String code, String name, String amount) {
        return map("feeCode", code, "feeName", name, "amount", amount);
    }

    private long lockPrice(long quoteBatchId, long wonQuoteId) {
        JsonNode node = lockResponse(quoteBatchId, wonQuoteId);
        assertTrue(node.path("success").asBoolean(), node.toString());
        return node.path("data").path("id").asLong();
    }

    private JsonNode lockResponse(long quoteBatchId, long wonQuoteId) {
        return post("/price-locks", map("quoteBatchId", quoteBatchId, "wonQuoteId", wonQuoteId), "dispatcher");
    }

    private long applyAdjustment(long lockId, String base, String taxRate,
                                 List<Map<String, Object>> surcharges, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("priceLockId", lockId);
        m.put("baseFreight", base);
        m.put("taxRate", taxRate);
        if (surcharges != null) {
            m.put("surcharges", surcharges);
        }
        m.put("changeReason", reason);
        JsonNode node = post("/price-adjustments", m, "dispatcher");
        assertTrue(node.path("success").asBoolean(), node.toString());
        return node.path("data").path("id").asLong();
    }

    private JsonNode quoteDetail(long quoteId) {
        // 报价只读详情（含附加费拆分），登录即可查看
        return get("/carrier-quotes/" + quoteId, "dispatcher").path("data").path("quote");
    }

    private long newOrder() {
        long nano = System.nanoTime();
        DrugCreateRequest dc = new DrugCreateRequest();
        dc.setDrugName("V8验收药品-" + nano);
        dc.setApprovalNo("国药准字V8-" + Math.abs(nano % 1000000));
        dc.setTempMin(new BigDecimal("2.00"));
        dc.setTempMax(new BigDecimal("8.00"));
        dc.setPackageSpec("1支/盒");
        Long drugId = drugService.create(dc).getId();
        OrderCreateRequest oc = new OrderCreateRequest();
        oc.setDrugId(drugId);
        oc.setReceiverOrg("V8验收医院");
        oc.setShipQty(10);
        return orderService.create(oc).getId();
    }

    private long newVehicle() {
        long nano = System.nanoTime();
        VehicleCreateRequest vr = new VehicleCreateRequest();
        vr.setPlateNo("测V8·冷" + Math.abs(nano % 1000000));
        vr.setCarrierName("V8验收物流-" + Math.abs(nano % 100000));
        vr.setDriverName("赵司机");
        return vehicleService.create(vr).getId();
    }

    private DispatchRequest newDispatch(long orderId, long vehicleId, long lockId) {
        DispatchRequest dr = new DispatchRequest();
        dr.setOrderId(orderId);
        dr.setVehicleId(vehicleId);
        dr.setPriceLockId(lockId);
        return dr;
    }

    private void expectBusiness(RunnableThrowing r, String contains) {
        try {
            r.run();
            throw new AssertionError("预期业务异常但未抛出: " + contains);
        } catch (com.evops.common.BusinessException e) {
            assertTrue(e.getMessage().contains(contains),
                    "异常信息应包含[" + contains + "]，实际=" + e.getMessage());
        } catch (Exception e) {
            throw new AssertionError("预期 BusinessException，实际=" + e, e);
        }
    }

    private void assertMoney(JsonNode node, String expected) {
        assertEquals(0, node.decimalValue().compareTo(new BigDecimal(expected)),
                "金额期望=" + expected + "，实际=" + node.asText());
    }

    private void assertFalseSuccess(JsonNode node, String messageContains) {
        assertTrue(!node.path("success").asBoolean(), "预期失败但成功: " + node);
        assertTrue(node.path("message").asText().contains(messageContains),
                "停点信息应包含[" + messageContains + "]，实际=" + node.path("message").asText());
    }

    interface RunnableThrowing {
        void run() throws Exception;
    }

    // ---------------- HTTP 辅助 ----------------

    private Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private JsonNode get(String path, String user) {
        String pass = passwordOf(user);
        ResponseEntity<String> resp = http.withBasicAuth(user, pass)
                .getForEntity(url(path), String.class);
        return read(resp);
    }

    private JsonNode post(String path, Map<String, Object> body, String user) {
        ResponseEntity<String> resp = rawPost(path, body, user, passwordOf(user));
        return read(resp);
    }

    private ResponseEntity<String> rawPost(String path, Map<String, Object> body, String user, String pass) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json;
        try {
            json = M.writeValueAsString(body == null ? new LinkedHashMap<>() : body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return http.withBasicAuth(user, pass)
                .postForEntity(url(path), new HttpEntity<>(json, headers), String.class);
    }

    private JsonNode read(ResponseEntity<String> resp) {
        try {
            return M.readTree(resp.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("解析响应失败: HTTP " + resp.getStatusCode() + " body=" + resp.getBody(), e);
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + BASE + path;
    }

    private String passwordOf(String user) {
        switch (user) {
            case "admin": return "admin123";
            case "dispatcher": return "dispatcher123";
            case "carrier": return "carrier123";
            case "qa": return "qa123";
            default: throw new IllegalArgumentException("未知测试账号: " + user);
        }
    }
}
