package com.meistudio.backend.controller;

import com.meistudio.backend.annotation.RateLimit;
import com.meistudio.backend.common.Result;
import com.meistudio.backend.entity.Document;
import com.meistudio.backend.service.KnowledgeService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kb")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /**
     * 上传文档至知识库（异步处理）。
     * POST /api/kb/upload
     * Form-data: file (TXT), apiKey (DashScope API Key)
     *
     * 接口会立即返回"文件已接收"，向量化在后台线程池中异步执行。
     * 前端可通过 GET /api/kb/documents 轮询文档状态（status: 0=处理中, 1=成功, 2=失败）。
     */
    @PostMapping("/upload")
    @RateLimit(maxRequests = 20, windowSeconds = 60, message = "上传过于频繁，请稍后再试")
    public Result<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return Result.error(400, "文件不能为空");
        }

        // 文件大小校验 (5MB)
        if (file.getSize() > 5 * 1024 * 1024) {
            return Result.error(400, "文件大小不能超过 5MB");
        }

        // 文件类型校验
        String fileName = file.getOriginalFilename();
        if (fileName != null) {
            String lower = fileName.toLowerCase();
            if (!lower.endsWith(".txt") && !lower.endsWith(".md") && !lower.endsWith(".json")
                    && !lower.endsWith(".pdf") && !lower.endsWith(".doc") && !lower.endsWith(".docx")) {
                return Result.error(400, "不支持该文件格式，仅支持 TXT/MD/PDF/Word");
            }
        }

        Long docId = knowledgeService.uploadDocument(file);
        return Result.success("文件已接收，正在后台处理中", Map.of("docId", docId, "status", "processing"));
    }

    /**
     * 语义检索知识库。
     * POST /api/kb/search
     * Body: { "question": "...", "apiKey": "...", "topK": 3 }
     */
    @PostMapping("/search")
    @RateLimit(maxRequests = 10, windowSeconds = 60, message = "检索过于频繁，请稍后再试")
    public Result<List<String>> search(@RequestBody Map<String, Object> body) {
        String question = (String) body.get("question");
        
        @SuppressWarnings("unchecked")
        List<Object> fileIdsRaw = (List<Object>) body.get("fileIds");
        List<Long> fileIds = null;
        if (fileIdsRaw != null) {
            fileIds = fileIdsRaw.stream()
                .map(id -> Long.valueOf(id.toString()))
                .toList();
        }

        // 获取 topK 参数，默认为 3
        int topK = body.containsKey("topK") ? (int) body.get("topK") : 3;

        List<String> results = knowledgeService.search(question, topK, fileIds);
        return Result.success(results);
    }

    /**
     * 查询当前用户的文档列表。
     * GET /api/kb/documents
     */
    @GetMapping("/documents")
    public Result<List<Document>> listDocuments() {
        return Result.success(knowledgeService.listDocuments());
    }

    /**
     * 删除文档及关联的向量数据。
     * DELETE /api/kb/documents/{id}
     */
    @DeleteMapping("/documents/{id}")
    public Result<Void> deleteDocument(@PathVariable("id") Long id) {
        knowledgeService.deleteDocument(id);
        return Result.success();
    }
}
