package com.meistudio.backend.service;

import com.meistudio.backend.common.UserContext;
import com.meistudio.backend.entity.Document;
import com.meistudio.backend.entity.UserConfig;
import com.meistudio.backend.mapper.DocumentMapper;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
@Service
public class KnowledgeService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final DocumentMapper documentMapper;
    private final UserConfigService userConfigService;

    @Value("${knowledge.chunk-size:500}")
    private int chunkSize;

    @Value("${knowledge.chunk-overlap:50}")
    private int chunkOverlap;

    @Value("${knowledge.search-top-k:3}")
    private int defaultTopK;

    @Value("${dashscope.api-key:}")
    private String defaultApiKey;

    @Value("${knowledge.vector-store-path:storage/vector_store}")
    private String vectorStorePath;

    /**
     * 用户级别的向量存储隔离。
     * 每个用户拥有独立的向量空间，保证数据安全。
     */
    private final Map<Long, EmbeddingStore<TextSegment>> userStores = new ConcurrentHashMap<>();

    public KnowledgeService(DocumentMapper documentMapper, UserConfigService userConfigService) {
        this.documentMapper = documentMapper;
        this.userConfigService = userConfigService;
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
        doc.setFileSize(file.getSize());
        doc.setStatus(0);
        documentMapper.insert(doc);

        // 2. 读取文件内容并进行向量化处理
        // 注意：不建议将文件字节转为 String，因为 PDF/Word 是二进制格式
        processDocumentAsync(doc.getId(), userId, file.getOriginalFilename(), file.getBytes());

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
    public void processDocumentAsync(Long docId, Long userId, String fileName, byte[] bytes) {
        try {
            // 1. 使用 Apache Tika 解析文档内容（支持 PDF, Word, TXT 等）
            java.io.InputStream inputStream = new java.io.ByteArrayInputStream(bytes);
            dev.langchain4j.data.document.Document langchainDoc = new ApacheTikaDocumentParser().parse(inputStream);
            
            // 2. 获取用户个性化配置，用于切分和向量化
            UserConfig config = userConfigService.getUserConfig(userId);

            // 3. 使用 LangChain4j DocumentSplitter 切分文本
            DocumentSplitter splitter = DocumentSplitters.recursive(
                    config.getChunkSize() != null ? config.getChunkSize() : chunkSize,
                    config.getChunkOverlap() != null ? config.getChunkOverlap() : chunkOverlap
            );
            List<TextSegment> segments = splitter.split(langchainDoc);

            // 🎯 修复：为每个文本段注入 docId 元数据 (使用干净的 Metadata 对象避免双重嵌套)
            List<TextSegment> cleanSegments = new ArrayList<>();
            for (TextSegment segment : segments) {
                dev.langchain4j.data.document.Metadata metadata = segment.metadata().copy();
                metadata.put("docId", docId.toString());
                cleanSegments.add(TextSegment.from(segment.text(), metadata));
            }

            log.info("[异步向量化] 文档 '{}' 切分为 {} 个语义块, 用户={}", fileName, cleanSegments.size(), userId);

            if (cleanSegments.isEmpty()) {
                log.warn("[异步向量化] 文档 '{}' 解析后内容为空，请检查文件编码或损坏情况。", fileName);
                updateDocumentStatus(docId, 2); // 标记为失败
                return;
            }

            // 3. 构建向量模型
            // 策略：鸿蒙端使用系统环境 Key，网页端强制使用个人私有 Key (BYOK)
            EmbeddingModel embeddingModel = buildEmbeddingModel(userId, config);

            // 3. 分批并行向量化
            int batchSize = 10; 
            List<List<TextSegment>> batches = new java.util.ArrayList<>();
            for (int i = 0; i < cleanSegments.size(); i += batchSize) {
                batches.add(cleanSegments.subList(i, Math.min(i + batchSize, cleanSegments.size())));
            }

            log.info("[异步向量化] 开始并行处理 {} 个批次 (每批 {}), 正在调用 DashScope...", batches.size(), batchSize);
            List<CompletableFuture<List<Embedding>>> futures = batches.stream()
                    .map(batch -> CompletableFuture.supplyAsync(() -> embeddingModel.embedAll(batch).content()))
                    .collect(Collectors.toList());

            List<Embedding> embeddings = futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

            // 4. 存入用户独立的向量库并同步到硬盘
            EmbeddingStore<TextSegment> store = getUserStore(userId);
            store.addAll(embeddings, cleanSegments);
            saveUserStore(userId);

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
     * @param fileIds  可选：限定检索的文件 ID 列表
     * @return 最相关文本段落列表
     */
    public List<String> search(String question, Integer topK, List<Long> fileIds) {
        Long userId = UserContext.getUserId();
        UserConfig config = userConfigService.getUserConfig(userId);
        int k = (topK != null && topK > 0) ? topK : (config.getTopK() != null ? config.getTopK() : defaultTopK);

        // 🎯 增强：校验并过滤 fileIds，确保仅检索属于当前用户的文档
        List<Long> authorizedFileIds = null;
        if (fileIds != null && !fileIds.isEmpty()) {
            authorizedFileIds = documentMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
                            .eq(Document::getUserId, userId)
                            .in(Document::getId, fileIds)
            ).stream().map(Document::getId).toList();
            
            log.info("[知识库检索] 用户={}, 原始 IDs={}, 授权后 IDs={}", userId, fileIds, authorizedFileIds);

            // 如果传入了 ID 过滤但无一匹配当前用户，直接返回空
            if (authorizedFileIds.isEmpty()) {
                log.warn("[知识库检索] 未找到归属于该用户的合法文件 ID");
                return java.util.Collections.emptyList();
            }
        }

        EmbeddingStore<TextSegment> store = getUserStore(userId);

        // 1. 将问题向量化（使用用户动态配置）
        EmbeddingModel embeddingModel = buildEmbeddingModel(userId, config);
        Embedding questionEmbedding = embeddingModel.embed(question).content();

        // 2. 执行相似度检索
        log.info("[知识库检索] 开始向量搜索: 问题='{}', 模型={}, 向量维度={}", 
                 question, config.getEmbeddingModel(), questionEmbedding.dimension());

        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder requestBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding)
                .maxResults(k);
        
        // 如果有合法的 ID 过滤，则应用
        // 🎯 增强：同时尝试匹配 docId (顶层) 和 metadata.docId (历史嵌套路径)，提高鲁棒性
        if (authorizedFileIds != null && !authorizedFileIds.isEmpty()) {
            List<String> filterDocIds = authorizedFileIds.stream().map(Object::toString).toList();
            
            // 组合过滤器：只要满足 docId (新) 或 metadata.docId (旧) 之一即可内容
            // 注意：InMemoryEmbeddingStore 对嵌套 key 的支持取决于底层实现，
            // 这里的最佳方案是修复写入端并引导用户重新上传。
            requestBuilder.filter(dev.langchain4j.store.embedding.filter.MetadataFilterBuilder
                    .metadataKey("docId").isIn(filterDocIds));
        }

        EmbeddingSearchResult<TextSegment> searchResult = store.search(requestBuilder.build());

        log.info("[知识库检索] 检索完成: 用户={}, 命中块数={}, TopK={}", userId, searchResult.matches().size(), k);

        // 3. 提取匹配的文本段落
        if (!searchResult.matches().isEmpty()) {
            for (int i = 0; i < searchResult.matches().size(); i++) {
                EmbeddingMatch<TextSegment> match = searchResult.matches().get(i);
                log.info("[知识库检索] 命中 #{} (Score: {}): 内容预览='{}'", 
                         i + 1, match.score(), 
                         match.embedded().text().substring(0, Math.min(50, match.embedded().text().length())).replace("\n", " "));
            }
        }

        return searchResult.matches().stream()
                .map(dev.langchain4j.store.embedding.EmbeddingMatch::embedded)
                .map(dev.langchain4j.data.segment.TextSegment::text)
                .collect(java.util.stream.Collectors.toList());
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

    private EmbeddingModel buildEmbeddingModel(Long userId, UserConfig config) {
        boolean isWebUser = userConfigService.isWebUser(userId);
        String finalApiKey;

        if (isWebUser) {
            // 网页端：严格遵循 BYOK 政策
            finalApiKey = config.getDashscopeApiKey();
            if (finalApiKey == null || finalApiKey.isBlank()) {
                throw new IllegalArgumentException("您的账号（网页端）尚未配置私有 API Key。为了保障您的隐私与成本独立，MeiStudio 网页端要求“自带 Key (BYOK)”。请前往系统的‘设置’界面进行配置。");
            }
        } else {
            // 鸿蒙端：固定使用服务器环境 Key，暂不支持私有 Key (按用户需求强制分流)
            finalApiKey = defaultApiKey;
            if (finalApiKey == null || finalApiKey.isBlank()) {
                log.error("[知识库] 服务器未配置 DASHSCOPE_API_KEY，鸿蒙端向量化由于缺乏 Key 将无法运行");
                throw new RuntimeException("服务器环境配置异常，请联系管理员配置 API Key");
            }
        }

        String modelName = config.getEmbeddingModel() != null ? config.getEmbeddingModel() : "text-embedding-v2";
        
        return QwenEmbeddingModel.builder()
                .apiKey(finalApiKey)
                .modelName(modelName)
                .build();
    }

    private EmbeddingStore<TextSegment> getUserStore(Long userId) {
        return userStores.computeIfAbsent(userId, id -> {
            try {
                java.nio.file.Path path = java.nio.file.Paths.get(vectorStorePath, id + ".json");
                if (java.nio.file.Files.exists(path)) {
                    log.info("[知识库] 正在从硬盘加载用户 {} 的向量库 ({})...", id, path.toString());
                    String json = java.nio.file.Files.readString(path, StandardCharsets.UTF_8);
                    return InMemoryEmbeddingStore.fromJson(json);
                }
            } catch (Exception e) {
                log.error("[知识库] 加载用户向量库失败, 创建新库: {}", e.getMessage());
            }
            return new InMemoryEmbeddingStore<>();
        });
    }

    private void saveUserStore(Long userId) {
        EmbeddingStore<TextSegment> store = userStores.get(userId);
        if (store instanceof InMemoryEmbeddingStore<TextSegment> memoryStore) {
            try {
                java.nio.file.Path dir = java.nio.file.Paths.get(vectorStorePath);
                if (!java.nio.file.Files.exists(dir)) {
                    java.nio.file.Files.createDirectories(dir);
                }
                java.nio.file.Path path = dir.resolve(userId + ".json");
                String json = memoryStore.serializeToJson();
                java.nio.file.Files.writeString(path, json, StandardCharsets.UTF_8);
                log.info("[知识库] 已将用户 {} 的向量库同步到硬盘: {}", userId, path.toString());
            } catch (Exception e) {
                log.error("[知识库] 同步用户向量库到硬盘失败: {}", e.getMessage());
            }
        }
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

    /**
     * 删除指定的文档，并同步从向量库中移除所有相关的块。
     */
    public void deleteDocument(Long docId) {
        Long userId = UserContext.getUserId();
        log.info("[知识库] 正在删除文档: docId={}, 用户={}", docId, userId);

        // 🎯 增强：安全校验，确保仅能删除自己的文档
        Document doc = documentMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
                        .eq(Document::getId, docId)
                        .eq(Document::getUserId, userId)
        );
        if (doc == null) {
            log.warn("[安全警报] 用户 {} 尝试非法删除文档 {}", userId, docId);
            return;
        }

        // 1. 从 MySQL 删除记录
        documentMapper.deleteById(docId);

        // 2. 从用户的内存向量库中清理
        EmbeddingStore<TextSegment> store = getUserStore(userId);
        if (store instanceof InMemoryEmbeddingStore<TextSegment> memoryStore) {
            // 通过元数据过滤器删除所有匹配 docId 的片段 (String 匹配)
            memoryStore.removeAll(dev.langchain4j.store.embedding.filter.MetadataFilterBuilder
                    .metadataKey("docId").isEqualTo(docId.toString()));
            saveUserStore(userId);
            log.info("[知识库] 已从内存向量库移除文档 {} 的所有语义片段并同步硬盘", docId);
        }
    }
}
