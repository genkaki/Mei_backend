package com.meistudio.backend.service;

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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
public class AgentService {

    @Value("${dashscope.api-key:}")
    private String apiKey;

    @Value("${agent.model-name:qwen-plus}")
    private String modelName;

    @Value("${agent.memory-window-size:10}")
    private int memoryWindowSize;

    private final WebSearchTool webSearchTool;
    private final McpClientManager mcpClientManager;

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
    interface SearchAgent {
        @SystemMessage("""
                你是 MeiStudio 智能助手，具备联网搜索能力。
                当前日期是：{{current_date}}。
                
                行为准则：
                1. 当用户询问实时信息（如天气、新闻、股价、最新政策、考试分数线等）时，你必须调用搜索工具获取最新数据，切勿凭空编造。
                2. 当用户询问常识性或技术性知识（如编程语法、数学公式、历史事实等）时，请直接回答，无需调用搜索工具。
                3. 引用搜索结果时，务必在回答末尾标注信息来源链接。
                4. 如果搜索结果与用户问题相关性不高，请如实告知，不要强行拼凑答案。
                5. 使用中文回答。
                """)
        String chat(@V("current_date") String currentDate, @UserMessage String userMessage);
    }

    public AgentService(WebSearchTool webSearchTool, McpClientManager mcpClientManager) {
        this.webSearchTool = webSearchTool;
        this.mcpClientManager = mcpClientManager;
    }

    /**
     * 处理用户的 Agent 对话请求。
     * 如果该用户还没有 Agent 实例，则延迟创建（Lazy Initialization）。
     *
     * @param userId      当前用户 ID（从 JWT 中提取，由 AuthInterceptor 注入 ThreadLocal）
     * @param userMessage 用户输入的自然语言消息
     * @return Agent 的最终回答（已整合搜索结果）
     */
    public String chat(Long userId, String userMessage) {
        log.info("[Agent] 收到请求: userId={}, message={}", userId,
                userMessage.length() > 100 ? userMessage.substring(0, 100) + "..." : userMessage);

        long startTime = System.currentTimeMillis();

        try {
            SearchAgent agent = userAgents.computeIfAbsent(userId, this::createAgent);
            String response = agent.chat(LocalDate.now().toString(), userMessage);

            long costMs = System.currentTimeMillis() - startTime;
            log.info("[Agent] 响应完成: userId={}, 耗时={}ms, 响应长度={}", userId, costMs, response.length());

            return response;

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startTime;
            log.error("[Agent] 处理失败: userId={}, 耗时={}ms, 错误={}", userId, costMs, e.getMessage());

            // 状态恢复逻辑：发生异常时清除该用户的 Agent 实例，防止后续尝试出现“Duplicated message”警告（因为 ChatMemory 状态可能已乱）
            userAgents.remove(userId);

            // 健壮性优化 (面试加分点): 专门处理大模型平台的 API 异常
            if (e.getCause() instanceof com.alibaba.dashscope.exception.ApiException apiException) {
                String errorCode = apiException.getStatus().getCode();
                if ("AllocationQuota.FreeTierOnly".equals(errorCode) || "AllocationQuota.Exhausted".equals(errorCode)) {
                    return "【系统提示】大模型免费额度已用尽。请在 application.yml 中更换 model-name (如 qwen-turbo) 或前往阿里云控制台开启计费。";
                }
                return "【大模型服务异常】" + apiException.getMessage();
            }

            return "【Agent 繁忙】服务暂时不可用，请稍后再试。错误类型: " + e.getClass().getSimpleName();
        }
    }

    /**
     * 清除指定用户的 Agent 对话记忆（重置上下文）。
     */
    public void clearMemory(Long userId) {
        userAgents.remove(userId);
        log.info("[Agent] 已清除用户会话记忆: userId={}", userId);
    }

    /**
     * 为指定用户创建独立的 Agent 实例。
     * 每个 Agent 实例包含：
     * - 一个通义千问对话模型（ChatLanguageModel）
     * - 一个联网搜索工具（WebSearchTool）
     * - 一个独立的对话记忆窗口（ChatMemory）
     * - 一个系统提示词（System Prompt），定义 Agent 的行为准则
     */
    private SearchAgent createAgent(Long userId) {
        log.info("[Agent] 为用户创建新的 Agent 实例: userId={}, model={}", userId, modelName);

        // 1. 构建 OpenAI 兼容模型（指向 DashScope 兼容接口）
        // 为什么要这么做？（面试亮点）：
        // 因为 DashScope 的原生 SDK 对新出的 Omni/多模态模型路径支持不佳，容易报 400 URL Error。
        // 使用 OpenAI 兼容模式不仅更稳定，还能体现架构的“解耦”思想——未来只需改配置，即可无缝切换 DeepSeek 或 GPT-4。
        ChatLanguageModel chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1") // 关键：指向 OpenAI 兼容端点
                .build();

        // 2. 构建用户独占的滑动窗口记忆
        ChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(memoryWindowSize)
                .build();

        // 3. 获取用户配置的 MCP 工具（动态能力扩展）
        List<ToolSpecification> mcpSpecs = mcpClientManager.getToolSpecsForUser(userId);
        List<ToolExecutor> mcpExecutors = mcpClientManager.getToolExecutorsForUser(userId);

        // 4. 使用 AiServices 动态代理构建 Agent
        var builder = AiServices.builder(SearchAgent.class)
                .chatLanguageModel(chatModel)
                .chatMemory(memory)
                .tools(webSearchTool);  // 内置工具始终可用

        // 动态注入 MCP 工具（如果用户配置了 MCP Server）
        if (!mcpSpecs.isEmpty()) {
            // 将 MCP 工具规格与执行器对应绑定
            Map<ToolSpecification, ToolExecutor> mcpToolMap = new java.util.LinkedHashMap<>();
            for (int i = 0; i < mcpSpecs.size(); i++) {
                mcpToolMap.put(mcpSpecs.get(i), mcpExecutors.get(i));
            }
            builder.tools(mcpToolMap);
            log.info("[Agent] 已注入 {} 个 MCP 工具: userId={}", mcpSpecs.size(), userId);
        }

        return builder.build();
    }
}
