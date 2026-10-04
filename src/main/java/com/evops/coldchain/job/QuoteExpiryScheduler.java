package com.evops.coldchain.job;

import com.evops.coldchain.service.QuoteBatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 报价过期扫描（V8）。
 *
 * 固定间隔把 valid_until 已过的 SUBMITTED 报价幂等推进为 EXPIRED（金额不删不改），
 * 并把超过截止时间且已无有效报价的比选中询价批次推进为 EXPIRED。
 * 定时线程无登录主体，操作者回退记为 system；扫描幂等可安全重放。
 * 通过 coldchain.quote-expiry.enabled=false 关闭（集成测试直调服务，避免定时器干扰）。
 */
@Component
@ConditionalOnProperty(name = "coldchain.quote-expiry.enabled", havingValue = "true", matchIfMissing = true)
public class QuoteExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuoteExpiryScheduler.class);

    @Resource
    private QuoteBatchService quoteBatchService;

    @Scheduled(fixedDelayString = "${coldchain.quote-expiry.fixed-delay-ms:60000}",
            initialDelayString = "${coldchain.quote-expiry.initial-delay-ms:15000}")
    public void scanExpiry() {
        try {
            Map<String, Object> summary = quoteBatchService.scanExpiry(LocalDateTime.now());
            int expiredQuotes = (int) summary.getOrDefault("expiredQuotes", 0);
            int expiredBatches = (int) summary.getOrDefault("expiredBatches", 0);
            if (expiredQuotes > 0 || expiredBatches > 0 || log.isDebugEnabled()) {
                log.info("报价过期扫描完成: {}", summary);
            }
        } catch (Exception e) {
            // 单次失败不影响下次调度；状态推进幂等，下轮按当前状态重算
            log.error("报价过期扫描失败，将在下个周期重试", e);
        }
    }
}
