package com.meistudio.backend.service;

import com.meistudio.backend.common.UserContext;
import com.meistudio.backend.entity.Document;
import com.meistudio.backend.mapper.DocumentMapper;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 核心知识库 (RAG) 服务。
 *
 * 职责：
 * 1. 解析上传的文本文件并切分为语义块（Chunking）。
 * 2. 调用阿里 DashScope Embedding API 进行向量化。
 * 3. 将向量存储到用户隔离的嵌入存储中（per-user isolation）。
 * 4. 当用户提问时执行相似度检索（Similarity Search）。
 *
 * 架构亮点：
 * - BYOK（Bring Your Own Key）：使用用户自己的 API Key 调用向量模型，开发者零成本。
 * - 异步解耦：文档向量化在独立线程池中执行，HTTP 请求立即返回，不阻塞。
 * - 分批处理：自动将大文档按每 10 个 chunk 一批发送给阿里，避免 batch size 限制。
 * - 租户隔离：每个用户拥有独立的向量存储空间，互不干扰。
 */
@Slf4j
@Service
public class KnowledgeService {

    private final DocumentMapper documentMapper;

    @Value("${knowledge.chunk-size:500}")
    private int chunkSize;

    @Value("${knowledge.chunk-overlap:50}")
    private int chunkOverlap;

    @Value("${knowledge.search-top-k:3}")
    private int defaultTopK;

    @Value("${dashscope.api-key:}")
    private String defaultApiKey;

    /**
     * 用户级别的向量存储隔离。
     * 每个用户拥有独立的向量空间，保证数据安全。
     */
    private final Map<Long, EmbeddingStore<TextSegment>> userStores = new ConcurrentHashMap<>();

    public KnowledgeService(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    /**
     * 上传并处理文档（同步部分：接收文件并保存记录）。
     * 向量化处理在异步线程中执行。
     *
     * @param file   上传的 TXT 文件
     * @return 文档记录 ID
     */
    public Long uploadDocument(MultipartFile file) throws IOException {
        Long userId = UserContext.getUserId();

        // 1. 在 MySQL 中创建文档记录（status=0，处理中）
        Document doc = new Document();
        doc.setUserId(userId);
        doc.setFileName(file.getOriginalFilename());
        doc.setStatus(0);
        documentMapper.insert(doc);

        // 2. 读取文件内容
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);

        // 3. 将向量化任务交给异步线程池
        processDocumentAsync(doc.getId(), userId, file.getOriginalFilename(), content);

        return doc.getId();
    }

    /**
     * 异步执行文档向量化任务。
     * 运行在独立的 vectorTaskExecutor 线程池中，不阻塞 HTTP 请求线程。
     *
     * @param docId    文档 ID
     * @param userId   用户 ID
     * @param fileName 文件名
     * @param content  文件内容
     */
    @Async("vectorTaskExecutor")
    public void processDocumentAsync(Long docId, Long userId, String fileName, String content) {
        try {
            // 1. 使用 LangChain4j DocumentSplitter 切分文本
            DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
            dev.langchain4j.data.document.Document langchainDoc =
                    dev.langchain4j.data.document.Document.from(content);
            List<TextSegment> segments = splitter.split(langchainDoc);

            log.info("[异步向量化] 文档 '{}' 切分为 {} 个语义块, 用户={}", fileName, segments.size(), userId);

            // 2. 构建向量模型（统一使用配置的 API Key）
            EmbeddingModel embeddingModel = buildEmbeddingModel();

            // 3. 分批向量化（每批 10 个，规避阿里 batch size 限制）
            List<Embedding> embeddings = new java.util.ArrayList<>();
            for (int i = 0; i < segments.size(); i += 10) {
                int end = Math.min(i + 10, segments.size());
                List<TextSegment> batch = segments.subList(i, end);
                embeddings.addAll(embeddingModel.embedAll(batch).content());
            }

            // 4. 存入用户独立的向量库
            EmbeddingStore<TextSegment> store = getUserStore(userId);
            store.addAll(embeddings, segments);

            // 5. 更新文档状态为成功 (status=1)
            updateDocumentStatus(docId, 1);
            log.info("[异步向量化] 文档 '{}' 向量化完成并存储成功, 用户={}", fileName, userId);

        } catch (Exception e) {
            // 标记文档为失败 (status=2)
            updateDocumentStatus(docId, 2);
            log.error("[异步向量化] 文档处理失败, 用户={}, 错误={}", userId, e.getMessage(), e);
        }
    }

    /**
     * 在知识库中进行语义检索。
     *
     * @param question 用户的问题
     * @param topK     返回的结果数量
     * @return 最相关文本段落列表
     */
    public List<String> search(String question, Integer topK) {
        Long userId = UserContext.getUserId();
        int k = (topK != null && topK > 0) ? topK : defaultTopK;

        EmbeddingStore<TextSegment> store = getUserStore(userId);

        // 1. 将问题向量化
        EmbeddingModel embeddingModel = buildEmbeddingModel();
        Embedding questionEmbedding = embeddingModel.embed(question).content();

        // 2. 执行相似度检索
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding)
                .maxResults(k)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = store.search(searchRequest);

        // 3. 提取匹配的文本段落
        return searchResult.matches().stream()
                .map(EmbeddingMatch::embedded)
                .map(TextSegment::text)
                .collect(Collectors.toList());
    }

    /**
     * 查询当前用户的所有已上传文档。
     */
    public List<Document> listDocuments() {
        Long userId = UserContext.getUserId();
        return documentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
                        .eq(Document::getUserId, userId)
                        .orderByDesc(Document::getCreateTime)
        );
    }

    // ==================== 私有工具方法 ====================

    private EmbeddingModel buildEmbeddingModel() {
        if (defaultApiKey == null || defaultApiKey.isBlank()) {
            throw new IllegalArgumentException("服务器未配置 DashScope API Key: dashscope.api-key");
        }
        return QwenEmbeddingModel.builder()
                .apiKey(defaultApiKey)
                .modelName("text-embedding-v4")
                .build();
    }

    private EmbeddingStore<TextSegment> getUserStore(Long userId) {
        return userStores.computeIfAbsent(userId, id -> new InMemoryEmbeddingStore<>());
    }

    /**
     * 更新文档处理状态。
     */
    private void updateDocumentStatus(Long docId, int status) {
        Document doc = new Document();
        doc.setId(docId);
        doc.setStatus(status);
        documentMapper.updateById(doc);
    }
}
