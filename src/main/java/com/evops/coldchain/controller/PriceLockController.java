package com.evops.coldchain.controller;

import com.evops.coldchain.dto.PriceAdjustmentApplyRequest;
import com.evops.coldchain.dto.PriceAdjustmentReviewRequest;
import com.evops.coldchain.dto.PriceLockRequest;
import com.evops.coldchain.dto.QuoteExceptionMarkRequest;
import com.evops.coldchain.dto.QuoteExceptionResolveRequest;
import com.evops.coldchain.entity.PriceAdjustment;
import com.evops.coldchain.entity.PriceLock;
import com.evops.coldchain.entity.QuoteException;
import com.evops.coldchain.service.PriceAdjustmentService;
import com.evops.coldchain.service.PriceLockTraceService;
import com.evops.coldchain.service.QuoteBatchService;
import com.evops.coldchain.service.QuoteExceptionService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 锁价比选与反查（V8）。
 *
 * - 锁价：一批报价中只能选唯一一个有效方案，中选即保存报价快照；
 * - 人工改价：申请 -> 审批（同意/驳回），原始报价快照永不动，执行价单列保留；
 * - 报价异常：只追加登记/闭环；
 * - 反查：从锁价单或运输批次定位原报价单、处理人、异常原因与改价审批链。
 */
@RestController
@RequestMapping("/api/cold-chain")
public class PriceLockController {

    private final QuoteBatchService quoteBatchService;
    private final PriceAdjustmentService adjustmentService;
    private final QuoteExceptionService exceptionService;
    private final PriceLockTraceService traceService;

    public PriceLockController(QuoteBatchService quoteBatchService,
                               PriceAdjustmentService adjustmentService,
                               QuoteExceptionService exceptionService,
                               PriceLockTraceService traceService) {
        this.quoteBatchService = quoteBatchService;
        this.adjustmentService = adjustmentService;
        this.exceptionService = exceptionService;
        this.traceService = traceService;
    }

    // ---------------- 锁价 ----------------

    /** 锁价：唯一中选一个有效报价并保存快照 */
    @PostMapping("/price-locks")
    public ApiResponse<PriceLock> lock(@Valid @RequestBody PriceLockRequest req) {
        return ApiResponse.ok(quoteBatchService.lock(req));
    }

    @GetMapping("/price-locks/{id}")
    public ApiResponse<PriceLock> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(quoteBatchService.requirePriceLock(id));
    }

    /** 报价详情只读（含附加费明细拆分）：比选/反查共用，登录即可查看 */
    @GetMapping("/carrier-quotes/{id}")
    public ApiResponse<Map<String, Object>> quoteDetail(@PathVariable("id") Long id) {
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("quote", quoteBatchService.requireQuote(id));
        view.put("surcharges", quoteBatchService.listSurcharges(id));
        return ApiResponse.ok(view);
    }

    /** 作废锁价单（未消费、非改价审批中），询价批次重新开放 */
    @PostMapping("/price-locks/{id}/void")
    public ApiResponse<PriceLock> voidLock(@PathVariable("id") Long id,
                                           @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? "" : body.getOrDefault("reason", "");
        return ApiResponse.ok(quoteBatchService.voidLock(id, reason));
    }

    /** 从锁价单反查：原报价单、报价/锁价/改价处理人、报价异常原因与闭环说明、操作留痕 */
    @GetMapping("/price-locks/{id}/trace")
    public ApiResponse<Map<String, Object>> traceByLock(@PathVariable("id") Long id) {
        return ApiResponse.ok(traceService.traceByLock(id));
    }

    /** 从冷链药品运输批次反查锁价与原报价链 */
    @GetMapping("/batches/{batchId}/price-trace")
    public ApiResponse<Map<String, Object>> traceByBatch(@PathVariable("batchId") Long batchId) {
        return ApiResponse.ok(traceService.traceByBatch(batchId));
    }

    // ---------------- 人工改价审批 ----------------

    /** 人工改价申请（锁价单进入 PRICE_PENDING，派车卡口拦截，原始报价保留） */
    @PostMapping("/price-adjustments")
    public ApiResponse<PriceAdjustment> applyAdjustment(@Valid @RequestBody PriceAdjustmentApplyRequest req) {
        return ApiResponse.ok(adjustmentService.apply(req));
    }

    @PostMapping("/price-adjustments/{id}/approve")
    public ApiResponse<PriceAdjustment> approveAdjustment(@PathVariable("id") Long id,
                                                          @Valid @RequestBody PriceAdjustmentReviewRequest req) {
        return ApiResponse.ok(adjustmentService.approve(id, req.getReviewNote()));
    }

    @PostMapping("/price-adjustments/{id}/reject")
    public ApiResponse<PriceAdjustment> rejectAdjustment(@PathVariable("id") Long id,
                                                         @Valid @RequestBody PriceAdjustmentReviewRequest req) {
        return ApiResponse.ok(adjustmentService.reject(id, req.getReviewNote()));
    }

    @GetMapping("/price-adjustments/pending")
    public ApiResponse<List<PriceAdjustment>> pendingAdjustments() {
        return ApiResponse.ok(adjustmentService.listPending());
    }

    /** 改价审批单详情（只读） */
    @GetMapping("/price-adjustments/{id}")
    public ApiResponse<PriceAdjustment> adjustmentDetail(@PathVariable("id") Long id) {
        return ApiResponse.ok(adjustmentService.requireAdjustment(id));
    }

    @GetMapping("/price-locks/{id}/adjustments")
    public ApiResponse<List<PriceAdjustment>> adjustmentsByLock(@PathVariable("id") Long id) {
        return ApiResponse.ok(adjustmentService.listByLock(id));
    }

    // ---------------- 报价异常 ----------------

    @PostMapping("/quote-exceptions")
    public ApiResponse<QuoteException> markException(@Valid @RequestBody QuoteExceptionMarkRequest req) {
        return ApiResponse.ok(exceptionService.mark(req));
    }

    @PostMapping("/quote-exceptions/{id}/resolve")
    public ApiResponse<QuoteException> resolveException(@PathVariable("id") Long id,
                                                        @Valid @RequestBody QuoteExceptionResolveRequest req) {
        req.setId(id);
        return ApiResponse.ok(exceptionService.resolve(req));
    }
}
