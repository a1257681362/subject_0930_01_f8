package com.evops.coldchain.controller;

import com.evops.coldchain.dto.PriceLockRequest;
import com.evops.coldchain.dto.QuoteBatchCreateRequest;
import com.evops.coldchain.dto.QuoteSubmitRequest;
import com.evops.coldchain.dto.QuoteWithdrawRequest;
import com.evops.coldchain.entity.CarrierQuote;
import com.evops.coldchain.entity.PriceLock;
import com.evops.coldchain.entity.QuoteBatch;
import com.evops.coldchain.service.QuoteBatchService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 承运商报价比选（V8）：询价批次、多承运商并行报价/撤报、比选视图、过期扫描。
 * 锁价动作见 {@link PriceLockController}。
 */
@RestController
@RequestMapping("/api/cold-chain/quote-batches")
public class QuoteBatchController {

    private final QuoteBatchService quoteBatchService;

    public QuoteBatchController(QuoteBatchService quoteBatchService) {
        this.quoteBatchService = quoteBatchService;
    }

    /** 发起询价批次（固化 路线×温区×箱型×服务等级 四维度） */
    @PostMapping
    public ApiResponse<QuoteBatch> create(@Valid @RequestBody QuoteBatchCreateRequest req) {
        return ApiResponse.ok(quoteBatchService.createBatch(req));
    }

    /** 比选中/已过期询价批次列表 */
    @GetMapping
    public ApiResponse<List<QuoteBatch>> listOpen() {
        return ApiResponse.ok(quoteBatchService.listOpenBatches());
    }

    /** 比选视图：询价批次 + 全部报价（含附加费、税额拆分，按含税总价升序）+ 锁价单 */
    @GetMapping("/{id}/comparison")
    public ApiResponse<Map<String, Object>> comparison(@PathVariable("id") Long id) {
        return ApiResponse.ok(quoteBatchService.comparison(id));
    }

    /** 承运商并行报价：金额只传基础运费+附加费+税率，税额/含税总价由服务端重算 */
    @PostMapping("/quotes")
    public ApiResponse<CarrierQuote> submitQuote(@Valid @RequestBody QuoteSubmitRequest req) {
        return ApiResponse.ok(quoteBatchService.submitQuote(req));
    }

    /** 承运商撤报（原报价行保留，可重新报价） */
    @PostMapping("/quotes/{id}/withdraw")
    public ApiResponse<CarrierQuote> withdraw(@PathVariable("id") Long id,
                                              @RequestBody(required = false) Map<String, String> body) {
        QuoteWithdrawRequest req = new QuoteWithdrawRequest();
        req.setQuoteId(id);
        req.setReason(body == null ? "" : body.getOrDefault("reason", ""));
        return ApiResponse.ok(quoteBatchService.withdraw(req));
    }

    /** 手动触发报价过期扫描（定时任务同逻辑，幂等） */
    @PostMapping("/expiry-scan")
    public ApiResponse<Map<String, Object>> expiryScan() {
        return ApiResponse.ok(quoteBatchService.scanExpiry(LocalDateTime.now()));
    }

    /** 作废询价批次（未锁价；全部报价行保留） */
    @PostMapping("/{id}/void")
    public ApiResponse<QuoteBatch> voidBatch(@PathVariable("id") Long id,
                                             @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? "" : body.getOrDefault("reason", "");
        return ApiResponse.ok(quoteBatchService.voidBatch(id, reason));
    }
}
