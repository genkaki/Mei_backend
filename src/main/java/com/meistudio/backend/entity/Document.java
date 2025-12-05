package com.meistudio.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String fileName;

    /**
     * 0 = Parsing, 1 = Success, 2 = Failed
     */
    private Integer status;

    private LocalDateTime createTime;
}
