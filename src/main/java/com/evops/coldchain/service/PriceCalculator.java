package com.evops.coldchain.service;

import com.evops.coldchain.dto.SurchargeItem;
import com.evops.common.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 报价金额计算（V8）：统一口径，报价提交与人工改价共用。
 *
 * 金额严格拆分，含税总价一律由本类重算，不信任客户端传入的总价：
 *   surchargeTotal = Σ 附加费明细
 *   taxAmount      = (baseFreight + surchargeTotal) × taxRate      （四舍五入保留两位）
 *   totalAmount    = baseFreight + surchargeTotal + taxAmount
 */
public final class PriceCalculator {

    public static final int MONEY_SCALE = 2;

    private PriceCalculator() {
    }

    /** 计算结果：附加费合计、税额、含税总价。 */
    public static final class Breakdown {
        private final BigDecimal surchargeTotal;
        private final BigDecimal taxAmount;
        private final BigDecimal totalAmount;

        Breakdown(BigDecimal surchargeTotal, BigDecimal taxAmount, BigDecimal totalAmount) {
            this.surchargeTotal = surchargeTotal;
            this.taxAmount = taxAmount;
            this.totalAmount = totalAmount;
        }

        public BigDecimal getSurchargeTotal() { return surchargeTotal; }
        public BigDecimal getTaxAmount() { return taxAmount; }
        public BigDecimal getTotalAmount() { return totalAmount; }
    }

    public static Breakdown calculate(BigDecimal baseFreight, BigDecimal taxRate, List<SurchargeItem> items) {
        if (baseFreight == null || baseFreight.signum() < 0) {
            throw new BusinessException("基础运费不能为空且不能为负");
        }
        if (taxRate == null || taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new BusinessException("税率不能为空，且必须位于 [0,1) 区间");
        }
        BigDecimal base = money(baseFreight);
        BigDecimal surcharge = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (items != null) {
            for (SurchargeItem item : items) {
                if (item.getAmount() == null || item.getAmount().signum() < 0) {
                    throw new BusinessException("附加费[" + item.getFeeName() + "]金额不能为空且不能为负");
                }
                surcharge = surcharge.add(money(item.getAmount()));
            }
        }
        BigDecimal taxable = base.add(surcharge);
        BigDecimal taxAmount = taxable.multiply(taxRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal total = taxable.add(taxAmount);
        return new Breakdown(surcharge, taxAmount, total);
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
