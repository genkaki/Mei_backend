package com.meistudio.backend.service.tool;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 联网搜索工具 —— 供 LangChain4j Agent 通过 Function Calling 自动调用。
 *
 * 技术要点：
 * 1. 使用 Jsoup 模拟浏览器 UA 爬取 Bing 搜索结果页，零成本、无需额外 API Key。
 * 2. 解析 HTML DOM 提取标题、摘要和链接，组装为大模型可直接消费的纯文本上下文。
 * 3. 内置连接超时与异常兜底，防止外部网络抖动拖垮 Agent 主线程。
 *
 * 架构说明：
 * - 该类被 @Component 注册为 Spring Bean，由 AgentService 注入到 LangChain4j 的 AiServices 中。
 * - @Tool 注解的方法会被 LangChain4j 框架自动暴露为 Function Calling 的可用工具。
 *   大模型在推理时，如果判断需要联网搜索，会自主生成对此方法的调用请求。
 */
@Component
public class WebSearchTool {
    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);

    @Value("${agent.max-search-results:5}")
    private int maxSearchResults;

    /**
     * 联网搜索指定关键词，返回实时搜索结果摘要。
     * <p>
     * 该方法由 LangChain4j 框架通过 Function Calling 机制自动调用。
     * 大模型会根据用户提问自主决定是否需要调用此工具，并自动传入合适的搜索关键词。
     *
     * @param query 搜索关键词（由大模型自动生成）
     * @return 格式化的搜索结果文本，包含标题、摘要和来源链接
     */
    @Tool("根据关键词搜索互联网获取实时信息。当用户询问最新新闻、实时数据、天气、股价、最新政策法规或任何你无法确定答案的时事问题时，请调用此工具。")
    public String searchWeb(String query) {
        log.info("[WebSearchTool] 开始搜索: query={}", query);
        long startTime = System.currentTimeMillis();

        try {
            // 使用 DuckDuckGo HTML 版本：结构最简单、对爬虫最友好、稳定性最高
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = "https://html.duckduckgo.com/html/?q=" + encodedQuery;

            Document doc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(10000)
                    .get();

            // DuckDuckGo HTML 专用解析逻辑
            Elements results = doc.select(".results_links_deep");
            List<SearchResult> searchResults = new ArrayList<>();

            for (Element result : results) {
                if (searchResults.size() >= maxSearchResults) break;

                Element titleElement = result.selectFirst(".result__a");
                Element snippetElement = result.selectFirst(".result__snippet");

                if (titleElement != null && snippetElement != null) {
                    String title = titleElement.text();
                    String link = titleElement.attr("href");
                    String snippet = snippetElement.text();
                    
                    if (!title.isEmpty() && !snippet.isEmpty()) {
                        searchResults.add(new SearchResult(title, snippet, link));
                    }
                }
            }

            // 兜底策略：如果 html 版本没结果，说明可能被反爬或 IP 受限，返回友好提示
            long costMs = System.currentTimeMillis() - startTime;
            log.info("[WebSearchTool] 搜索完成: query={}, 结果数={}, 耗时={}ms", query, searchResults.size(), costMs);

            if (searchResults.isEmpty()) {
                return "暂未通过联网搜索获取到关于 \"" + query + "\" 的详细信息。可能由于网络波动或搜索限制，建议稍后再试或换个关键词。";
            }

            return formatResults(query, searchResults);

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startTime;
            log.error("[WebSearchTool] 搜索过程中发生致命错误: query={}, 耗时={}ms, 错误={}", query, costMs, e.getMessage());
            return "联网搜索服务暂时无法连接。建议基于你的已有知识回答问题。";
        }
    }

    /**
     * 将原始搜索结果格式化为大模型易于理解的结构化文本。
     */
    private String formatResults(String query, List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是关于 \"").append(query).append("\" 的互联网实时搜索结果：\n\n");

        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append("【结果 ").append(i + 1).append("】\n");
            sb.append("标题: ").append(r.title).append("\n");
            sb.append("摘要: ").append(r.snippet).append("\n");
            sb.append("来源: ").append(r.link).append("\n\n");
        }

        sb.append("请基于以上搜索结果回答用户的问题，务必在回答中注明信息来源。");
        return sb.toString();
    }

    /**
     * 搜索结果内部数据模型。
     */
    private record SearchResult(String title, String snippet, String link) {
    }
}
