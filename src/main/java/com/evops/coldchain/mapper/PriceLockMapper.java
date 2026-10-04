package com.evops.coldchain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.coldchain.entity.PriceLock;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PriceLockMapper extends BaseMapper<PriceLock> {
}
