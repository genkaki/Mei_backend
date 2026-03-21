package com.meistudio.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库文档上传请求 DTO。
 * 支持 HarmonyOS 客户端发送的 JSON 格式上传。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeUploadRequest {
    
    /** 文件名 */
    private String fileName;
    
    /** 
     * 文件内容。
     * 对于文本文件（TXT/MD），直接传字符串；
     * 对于二进制文件（PDF/Word），建议传 Base64（目前后端 Service 通过 Tika 处理）。
     */
    private String content;
}
