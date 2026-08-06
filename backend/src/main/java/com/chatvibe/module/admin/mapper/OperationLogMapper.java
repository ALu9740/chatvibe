package com.chatvibe.module.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatvibe.module.admin.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper
 *
 * @author Alu
 * @date 2026-08-06
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
