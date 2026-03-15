# MeiStudio Backend - 高可用 RAG 与 MCP 智能 Agent 核心

MeiStudio 的后端核心模块。本项目实现了基于 **LangChain4j** 的 RAG (Retrieval-Augmented Generation) 架构，并独立实现了 **MCP (Model Context Protocol)** 协议栈，赋予 Agent 动态扩展工具的能力。

本项目已成功适配并发步于 **华为应用商店 (AppGallery)** 端的 HarmonyOS 应用。

---

## 🌟 核心特性

- **RAG 知识库链路**：实现 PDF/Word 文档解析、向量化（Redis Vector）与语义检索，支持私有知识库问答。
- **MCP 协议实现**：独立实现了基于 JSON-RPC 2.0 的 MCP 客户端，支持 Agent 在运行时动态发现并调用外部工具。
- **高可用架构设计**：
  - **Fail-Open 策略**：针对 Redis 依赖的限流器实现了故障自动放行，确保系统在断网等极端场景下的业务连续性。
  - **滑动窗口记忆管理**：重写了 LangChain4j 的上下文窗口逻辑，平衡推理一致性与 Token 开销。
- **异步流式推送**：基于 SSE + HTTP POST 的组合方案，实现了非阻塞的 Agent 工具指令下发与响应。

---

## 🛠 技术栈

- **核心框架**: Spring Boot 3.2+
- **AI 编排**: LangChain4j 0.35+
- **数据库**: MySQL 8.0, Redis (Stack/Vector)
- **协议栈**: JSON-RPC 2.0, SSE, HTTP/1.1
- **持久层**: MyBatis-Plus

---

## 🚀 快速启动

### 预防针：环境要求
- Java 21+
- Maven 3.6+
- MySQL & Redis

### 1. 配置
复制 `application.yml.example` 为 `application.yml`，并填入你的 API Key:
```bash
cp src/main/resources/application.yml.example src/main/resources/application.yml
```

### 2. 数据库初始化
执行 `sql/` 目录下的建表脚本。

### 3. 运行
```bash
mvn spring-boot:run
```

---

## 🏗 开源声明与致谢

本项目作为 [MeiStudio](https://consumer.huawei.com/en/mobileservices/appgallery/) 的核心后端引擎，旨在探索开源协议（MCP）在 Java 生态下的落地实践。欢迎任何形式的 Issue 与 PR！

---

## 📄 LICENSE

[MIT License](LICENSE)
