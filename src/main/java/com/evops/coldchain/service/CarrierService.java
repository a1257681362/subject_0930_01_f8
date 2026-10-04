package com.evops.coldchain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.coldchain.CcConstants;
import com.evops.coldchain.dto.CarrierCreateRequest;
import com.evops.coldchain.entity.Carrier;
import com.evops.coldchain.mapper.CarrierMapper;
import com.evops.coldchain.mapper.SequenceMapper;
import com.evops.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * 承运商主档服务（V8）。
 * 建档发稳定编号 CY+序列；撤回只置 REVOKED，不删行——历史报价、锁价快照仍可追溯到原主体。
 */
@Service
public class CarrierService {

    @Resource
    private CarrierMapper carrierMapper;
    @Resource
    private SequenceMapper sequenceMapper;
    @Resource
    private OpLogService opLogService;

    @Transactional
    public Carrier create(CarrierCreateRequest req) {
        String name = req.getCarrierName().trim();
        Long exists = carrierMapper.selectCount(new QueryWrapper<Carrier>().eq("carrier_name", name));
        if (exists != null && exists > 0) {
            throw new BusinessException("承运商名称已存在: " + name);
        }
        Carrier carrier = new Carrier();
        carrier.setCarrierNo(generateCarrierNo());
        carrier.setCarrierName(name);
        carrier.setContactName(req.getContactName());
        carrier.setContactPhone(req.getContactPhone());
        carrier.setQualification(req.getQualification());
        carrier.setStatus(CcConstants.ACTIVE);
        carrier.setRemark(req.getRemark());
        carrierMapper.insert(carrier);

        opLogService.log(CcConstants.ENT_CARRIER, carrier.getId(), null, CcConstants.ACT_CARRIER_CREATE,
                String.format("承运商建档 %s：%s（联系人=%s，资质=%s）",
                        carrier.getCarrierNo(), carrier.getCarrierName(),
                        carrier.getContactName(), carrier.getQualification()));
        return carrier;
    }

    @Transactional
    public Carrier revoke(Long carrierId, String reason) {
        Carrier carrier = requireCarrier(carrierId);
        if (CcConstants.REVOKED.equals(carrier.getStatus())) {
            throw new BusinessException("承运商已撤回，不能重复撤回: " + carrier.getCarrierNo());
        }
        carrier.setStatus(CcConstants.REVOKED);
        carrierMapper.updateById(carrier);
        opLogService.log(CcConstants.ENT_CARRIER, carrier.getId(), null, CcConstants.ACT_CARRIER_REVOKE,
                String.format("承运商 %s（%s）撤回作废，历史报价与锁价快照保留可追溯。原因：%s",
                        carrier.getCarrierNo(), carrier.getCarrierName(), reason));
        return carrier;
    }

    public Carrier requireCarrier(Long carrierId) {
        Carrier carrier = carrierMapper.selectById(carrierId);
        if (carrier == null) {
            throw new BusinessException("承运商不存在: " + carrierId);
        }
        return carrier;
    }

    public Carrier requireActiveCarrier(Long carrierId) {
        Carrier carrier = requireCarrier(carrierId);
        if (CcConstants.REVOKED.equals(carrier.getStatus())) {
            throw new BusinessException("承运商已撤回，不能参与报价: " + carrier.getCarrierNo());
        }
        return carrier;
    }

    public List<Carrier> list() {
        return carrierMapper.selectList(new QueryWrapper<Carrier>().orderByAsc("id"));
    }

    /** 承运商编号：CY + 8 位（独立序列，避免与询价批次号段混用） */
    private String generateCarrierNo() {
        return String.format("CY%08d", sequenceMapper.nextCarrierNo());
    }
}
