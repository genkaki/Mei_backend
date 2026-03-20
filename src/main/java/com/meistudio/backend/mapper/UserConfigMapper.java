package com.meistudio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meistudio.backend.entity.UserConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户配置 Mapper。
 */
@Mapper
public interface UserConfigMapper extends BaseMapper<UserConfig> {
}
