package com.evops.coldchain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.coldchain.CcConstants;
import com.evops.coldchain.dto.BoxTypeCreateRequest;
import com.evops.coldchain.entity.BoxType;
import com.evops.coldchain.mapper.BoxTypeMapper;
import com.evops.coldchain.mapper.SequenceMapper;
import com.evops.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * 箱型主档服务（V8）。箱型代码库内唯一；撤回只置 REVOKED，已被询价批次/报价快照引用的箱型仍可追溯。
 */
@Service
public class BoxTypeService {

    @Resource
    private BoxTypeMapper boxTypeMapper;
    @Resource
    private SequenceMapper sequenceMapper;
    @Resource
    private OpLogService opLogService;

    @Transactional
    public BoxType create(BoxTypeCreateRequest req) {
        String code = req.getBoxTypeCode().trim();
        Long exists = boxTypeMapper.selectCount(new QueryWrapper<BoxType>().eq("box_type_code", code));
        if (exists != null && exists > 0) {
            throw new BusinessException("箱型代码已存在: " + code);
        }
        BoxType boxType = new BoxType();
        boxType.setBoxTypeNo(String.format("BX%08d", sequenceMapper.nextBoxTypeNo()));
        boxType.setBoxTypeCode(code);
        boxType.setBoxTypeName(req.getBoxTypeName().trim());
        boxType.setVolumeLiter(req.getVolumeLiter());
        boxType.setStatus(CcConstants.ACTIVE);
        boxType.setRemark(req.getRemark());
        boxTypeMapper.insert(boxType);

        opLogService.log(CcConstants.ENT_BOX_TYPE, boxType.getId(), null, CcConstants.ACT_BOX_TYPE_CREATE,
                String.format("箱型建档 %s：%s（%s，容积=%sL）",
                        boxType.getBoxTypeNo(), boxType.getBoxTypeCode(),
                        boxType.getBoxTypeName(), boxType.getVolumeLiter()));
        return boxType;
    }

    @Transactional
    public BoxType revoke(Long boxTypeId) {
        BoxType boxType = requireBoxType(boxTypeId);
        if (CcConstants.REVOKED.equals(boxType.getStatus())) {
            throw new BusinessException("箱型已撤回，不能重复撤回: " + boxType.getBoxTypeNo());
        }
        boxType.setStatus(CcConstants.REVOKED);
        boxTypeMapper.updateById(boxType);
        opLogService.log(CcConstants.ENT_BOX_TYPE, boxType.getId(), null, CcConstants.ACT_BOX_TYPE_REVOKE,
                String.format("箱型 %s（%s）撤回作废，已被报价快照引用的记录保留可追溯",
                        boxType.getBoxTypeNo(), boxType.getBoxTypeCode()));
        return boxType;
    }

    public BoxType requireBoxType(Long boxTypeId) {
        BoxType boxType = boxTypeMapper.selectById(boxTypeId);
        if (boxType == null) {
            throw new BusinessException("箱型不存在: " + boxTypeId);
        }
        return boxType;
    }

    public BoxType requireActiveBoxType(Long boxTypeId) {
        BoxType boxType = requireBoxType(boxTypeId);
        if (CcConstants.REVOKED.equals(boxType.getStatus())) {
            throw new BusinessException("箱型已撤回，不能发起报价: " + boxType.getBoxTypeNo());
        }
        return boxType;
    }

    public List<BoxType> list() {
        return boxTypeMapper.selectList(new QueryWrapper<BoxType>().orderByAsc("id"));
    }
}
