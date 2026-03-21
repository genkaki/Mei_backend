package com.meistudio.backend.service;

import com.meistudio.backend.entity.McpServer;
import com.meistudio.backend.entity.UserConfig;
import com.meistudio.backend.mcp.client.McpClientManager;
import com.meistudio.backend.service.tool.WebSearchTool;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 核心编排服务。
 *
 * 架构要点（面试高频考点）：
 * 1. 基于 LangChain4j 的 AiServices 动态代理机制构建 Agent 执行器。
 *    框架在运行时通过 Java 动态代理（java.lang.reflect.Proxy）自动生成接口实现，
 *    将方法调用转化为"大模型推理 → 解析 Function Calling 请求 → 执行 Tool → 再推理"的 ReAct 循环。
 *
 * 2. 多租户会话隔离：
 *    每个用户拥有独立的 ChatMemory（基于 userId 键隔离），保证多用户并发时对话上下文互不干扰。
 *    使用 ConcurrentHashMap 保证线程安全，避免 synchronized 的粗粒度锁竞争。
 *
 * 3. Tool 注册与 Function Calling 机制：
 *    使用 LangChain4j 的 @Tool 注解将 WebSearchTool 的方法自动注册为大模型可调用的外部函数。
 *    大模型在推理时会自主判断：对于实时问题（如天气、新闻），自行生成 Tool 调用请求；
 *    对于知识类问题（如 Java 语法），则直接回答，不调用任何 Tool。
 *
 * 4. 上下文窗口管理（滑动窗口策略）：
 *    采用 MessageWindowChatMemory 限制上下文长度为最近 N 轮对话，
 *    既防止 Token 消耗无限膨胀，又保持足够的对话连贯性。
 */
@Service
public class AgentService {
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    @Value("${dashscope.api-key:}")
    private String apiKey;

    /**
     * 获取系统全局配置的 DashScope API Key（从环境变量或配置文件读取）。
     */
    public String getSystemDefaultApiKey() {
        return this.apiKey;
    }

    @Value("${agent.model-name:qwen-plus}")
    private String modelName;

    @Value("${agent.memory-window-size:10}")
    private int memoryWindowSize;

    private final WebSearchTool webSearchTool;
    private final McpClientManager mcpClientManager;
    private final KnowledgeService knowledgeService;
    private final UserConfigService userConfigService;

    /**
     * 用户级别的 Agent 会话隔离。
     * Key: userId, Value: 该用户的 Agent 代理实例。
     * 每个用户的 Agent 实例内部持有独立的 ChatMemory，保证多租户对话上下文隔离。
     */
    private final Map<Long, SearchAgent> userAgents = new ConcurrentHashMap<>();

    /**
     * Agent 对话接口定义。
     * LangChain4j 的 AiServices 框架会在运行时通过 Java 动态代理自动生成此接口的实现类。
     * 每次调用 chat() 方法时，框架自动执行以下 ReAct 循环：
     *   1. 将用户消息 + 历史上下文发送给大模型
     *   2. 如果大模型返回 Tool 调用请求 → 执行对应的 @Tool 方法
     *   3. 将 Tool 的返回结果再次发送给大模型
     *   4. 重复 2-3 直到大模型返回最终文本回答
     *
     * @SystemMessage 注解定义了 Agent 的系统提示词（Prompt Engineering），
     * 引导大模型在推理时正确判断何时需要调用搜索工具。
     */
    public interface SearchAgent {
        @SystemMessage("""
                你是 MeiStudio 智能助手，当前大模型驱动由 {{model_name}} 提供。
                你具备联网搜索和 MCP 插件能力。
                当前日期是：{{current_date}}。
                
                行为准则：
                1. 当用户询问实时信息（如天气、新闻、最新趋势等）时，你必须调用搜索工具。
                2. 当用户要求画图、查询专业数据库或执行特定任务时，你必须通过 MCP 插件调用对应的工具。
                3. 请使用中文回答，并保持专业、友好的语气。
                """)
        String chat(@V("current_date") String currentDate, @V("model_name") String modelName, @UserMessage String userMessage);
    }

    /**
     * 流式 Agent 接口。
     */
    public interface StreamingSearchAgent {
        @SystemMessage("""
                你是 MeiStudio 智能助手，当前大模型驱动由 {{model_name}} 提供。
                你具备联网搜索和 MCP 插件能力。
                当前日期是：{{current_date}}。
                
                行为准则：
                1. 当用户询问实时信息（如天气、新闻、最新趋势等）时，你必须调用搜索工具。
                2. 当用户要求画图、查询专业数据库或执行特定任务时，你必须通过 MCP 插件调用对应的工具。
                3. 请使用中文回答，并保持专业、友好的语气。
                """)
        TokenStream chat(@V("current_date") String currentDate, @V("model_name") String modelName, @UserMessage String userMessage);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentConfig {
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private List<Long> fileIds;
        private List<Long> mcpServerIds;
        @Builder.Default
        private Double temperature = 0.7;
    }

    public AgentService(WebSearchTool webSearchTool, McpClientManager mcpClientManager, 
                        KnowledgeService knowledgeService, UserConfigService userConfigService) {
        this.webSearchTool = webSearchTool;
        this.mcpClientManager = mcpClientManager;
        this.knowledgeService = knowledgeService;
        this.userConfigService = userConfigService;
    }

    /**
     * 处理用户的 Agent 对话请求。
     * 如果该用户还没有 Agent 实例，则延迟创建（Lazy Initialization）。
     *
     * @param userId      当前用户 ID（从 JWT 中提取，由 AuthInterceptor 注入 ThreadLocal）
     * @param userMessage 用户输入的自然语言消息
     * @return Agent 的最终回答（已整合搜索结果）
     */
    public String chat(Long userId, String userMessage, List<Long> fileIds) {
        AgentConfig config = AgentConfig.builder()
                .fileIds(fileIds)
                .build();
        return chat(userId, userMessage, config);
    }

    public String chat(Long userId, String userMessage, AgentConfig config) {
        log.info("[Agent] 收到请求: userId={}, message={}", userId, 
                userMessage.length() > 60 ? userMessage.substring(0, 60) + "..." : userMessage);

        String enrichedMessage = enrichMessageWithRAG(userMessage, config.getFileIds());
        UserConfig userConfig = userConfigService.getUserConfig(userId);
        SearchAgent agent = userAgents.computeIfAbsent(userId, id -> createAgent(id, config, userConfig));
        String effectiveModelName = (config.getModelName() != null && !config.getModelName().isEmpty()) ? config.getModelName() : userConfig.getChatModel();
        return agent.chat(LocalDate.now().toString(), effectiveModelName, enrichedMessage);
    }

    public TokenStream chatStream(Long userId, String userMessage, AgentConfig config) {
        log.info("[Agent] 收到流式请求: userId={}, model={}", userId, config.getModelName());
        
        String enrichedMessage = enrichMessageWithRAG(userMessage, config.getFileIds());
        UserConfig userConfig = userConfigService.getUserConfig(userId);
        StreamingSearchAgent agent = createStreamingAgent(userId, config, userConfig);
        String effectiveModelName = (config.getModelName() != null && !config.getModelName().isEmpty()) ? config.getModelName() : userConfig.getChatModel();
        
        return agent.chat(LocalDate.now().toString(), effectiveModelName, enrichedMessage);
    }

    /**
     * 清除指定用户的 Agent 对话记忆（重置上下文）。
     */
    public void clearMemory(Long userId) {
        userAgents.remove(userId);
        log.info("[Agent] 已清除用户会话记忆: userId={}", userId);
    }

    private String enrichMessageWithRAG(String userMessage, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return userMessage;
        }
        try {
            List<String> contextChunks = knowledgeService.search(userMessage, 10, fileIds);
            if (!contextChunks.isEmpty()) {
                String context = String.join("\n\n", contextChunks);
                return String.format("""
                        【参考资料】以下是从用户上传的附件中检索出的相关内容，请优先根据这些资料回答：
                        ---
                        %s
                        
                        ---
                        【用户问题】
                        %s
                        
                        请结合参考资料给出准确、详细的回答。如果参考资料未直接提及，可以基于模型原有知识进行补充，但应明确区分。
                        """, context, userMessage);
            }
        } catch (Exception e) {
            log.warn("[Agent] RAG 检索失败: {}", e.getMessage());
        }
        return userMessage;
    }

    private SearchAgent createAgent(Long userId, AgentConfig config, UserConfig userConfig) {
        ChatLanguageModel chatModel = buildChatModel(config, userConfig);
        
        ChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(memoryWindowSize)
                .build();

        List<ToolSpecification> mcpSpecs = mcpClientManager.getToolSpecsForUser(userId, config.getMcpServerIds());
        List<ToolExecutor> mcpExecutors = mcpClientManager.getToolExecutorsForUser(userId, config.getMcpServerIds());

        var builder = AiServices.builder(SearchAgent.class)
                .chatLanguageModel(chatModel)
                .chatMemory(memory)
                .tools(webSearchTool);

        if (!mcpSpecs.isEmpty()) {
            Map<ToolSpecification, ToolExecutor> mcpToolMap = new java.util.LinkedHashMap<>();
            for (int i = 0; i < mcpSpecs.size(); i++) {
                mcpToolMap.put(mcpSpecs.get(i), mcpExecutors.get(i));
            }
            builder.tools(mcpToolMap);
        }

        return builder.build();
    }

    private StreamingSearchAgent createStreamingAgent(Long userId, AgentConfig config, UserConfig userConfig) {
        StreamingChatLanguageModel chatModel = buildStreamingChatModel(config, userConfig);
        
        ChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(memoryWindowSize)
                .build();

        List<ToolSpecification> mcpSpecs = mcpClientManager.getToolSpecsForUser(userId, config.getMcpServerIds());
        List<ToolExecutor> mcpExecutors = mcpClientManager.getToolExecutorsForUser(userId, config.getMcpServerIds());

        var builder = AiServices.builder(StreamingSearchAgent.class)
                .streamingChatLanguageModel(chatModel)
                .chatMemory(memory)
                .tools(webSearchTool);

        if (!mcpSpecs.isEmpty()) {
            Map<ToolSpecification, ToolExecutor> mcpToolMap = new java.util.LinkedHashMap<>();
            for (int i = 0; i < mcpSpecs.size(); i++) {
                mcpToolMap.put(mcpSpecs.get(i), mcpExecutors.get(i));
            }
            builder.tools(mcpToolMap);
        }

        return builder.build();
    }

    private ChatLanguageModel buildChatModel(AgentConfig config, UserConfig userConfig) {
        String effectiveApiKey = (config.getApiKey() != null && !config.getApiKey().isEmpty() && !config.getApiKey().contains("managed-by-backend")) 
                ? config.getApiKey() : userConfig.getDashscopeApiKey();
        
        if (effectiveApiKey == null || effectiveApiKey.isBlank()) {
            throw new IllegalArgumentException("AI 对话引擎尚未就绪。此功能（基础对话 / 插件）需要您“自带密钥 (BYOK)”才能运行。请前往‘系统设置’配置您的私密 API 秘钥。");
        }
        String effectiveModelName = (config.getModelName() != null && !config.getModelName().isEmpty() && !config.getModelName().equals("meistudio-cloud-agent")) 
                ? config.getModelName() : userConfig.getChatModel();
        String effectiveBaseUrl = (config.getBaseUrl() != null && !config.getBaseUrl().isEmpty() && !config.getBaseUrl().equals("managed-by-backend")) 
                ? config.getBaseUrl() : "https://dashscope.aliyuncs.com/compatible-mode/v1";

        return OpenAiChatModel.builder()
                .apiKey(effectiveApiKey)
                .modelName(effectiveModelName)
                .baseUrl(effectiveBaseUrl)
                .temperature(config.getTemperature())
                .build();
    }

    private StreamingChatLanguageModel buildStreamingChatModel(AgentConfig config, UserConfig userConfig) {
        String effectiveApiKey = (config.getApiKey() != null && !config.getApiKey().isEmpty() && !config.getApiKey().contains("managed-by-backend")) 
                ? config.getApiKey() : userConfig.getDashscopeApiKey();

        if (effectiveApiKey == null || effectiveApiKey.isBlank()) {
            throw new IllegalArgumentException("AI 对话引擎初始化失败。网页端用户请在‘系统设置’中填入私有的 DashScope API Key。");
        }
        String effectiveModelName = (config.getModelName() != null && !config.getModelName().isEmpty() && !config.getModelName().equals("meistudio-cloud-agent")) 
                ? config.getModelName() : userConfig.getChatModel();
        String effectiveBaseUrl = (config.getBaseUrl() != null && !config.getBaseUrl().isEmpty() && !config.getBaseUrl().equals("managed-by-backend")) 
                ? config.getBaseUrl() : "https://dashscope.aliyuncs.com/compatible-mode/v1";

        return dev.langchain4j.model.openai.OpenAiStreamingChatModel.builder()
                .apiKey(effectiveApiKey)
                .modelName(effectiveModelName)
                .baseUrl(effectiveBaseUrl)
                .temperature(config.getTemperature())
                .build();
    }
}
