package com.meistudio.backend.controller;

import com.meistudio.backend.annotation.RateLimit;
import com.meistudio.backend.common.Result;
import com.meistudio.backend.entity.Document;
import com.meistudio.backend.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /**
     * 上传文档至知识库（异步处理）。
     * POST /api/kb/upload
     * Form-data: file (TXT), apiKey (DashScope API Key)
     *
     * 接口会立即返回"文件已接收"，向量化在后台线程池中异步执行。
     * 前端可通过 GET /api/kb/documents 轮询文档状态（status: 0=处理中, 1=成功, 2=失败）。
     */
    @PostMapping("/upload")
    @RateLimit(maxRequests = 3, windowSeconds = 60, message = "上传过于频繁，请稍后再试")
    public Result<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return Result.error(400, "文件不能为空");
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
        Integer topK = body.get("topK") != null ? (Integer) body.get("topK") : null;

        if (question == null || question.isBlank()) {
            return Result.error(400, "问题不能为空");
        }

        List<String> results = knowledgeService.search(question, topK);
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
}
