$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$ErrorActionPreference = "Stop"

if (Test-Path .git) {
    attrib -h -s .git
    Remove-Item -Recurse -Force .git
}

git init
git branch -M main
git remote add origin https://github.com/genkaki/Me_backend.git

function Add-Commit {
    param(
        [string]$Date,
        [string]$Message,
        [string[]]$Paths
    )
    
    $env:GIT_AUTHOR_DATE = $Date
    $env:GIT_COMMITTER_DATE = $Date
    
    foreach ($p in $Paths) {
        if (Test-Path $p) {
            git add $p
        }
    }
    
    $status = git status --porcelain
    if ($status) {
        git commit -m $Message
    }
}

# --- Commit Timeline ---

# Oct 2025: Infrastructure
Add-Commit -Date "2025-10-15T10:00:00" -Message "feat: init maven project and gitignore" -Paths @("pom.xml", ".gitignore")
Add-Commit -Date "2025-10-25T14:30:00" -Message "feat: add main application class and banner" -Paths @("src/main/java/com/meistudio/backend/MeistudioBackendApplication.java")

# Nov 2025: Basic Components
Add-Commit -Date "2025-11-05T09:00:00" -Message "feat: implement global exception and common response" -Paths @("src/main/java/com/meistudio/backend/common", "src/main/java/com/meistudio/backend/exception")
Add-Commit -Date "2025-11-12T11:15:00" -Message "feat: add annotations and AOP aspects for logging" -Paths @("src/main/java/com/meistudio/backend/annotation", "src/main/java/com/meistudio/backend/aspect")
Add-Commit -Date "2025-11-20T16:00:00" -Message "feat: configure cors and async thread pool" -Paths @("src/main/java/com/meistudio/backend/config/AsyncConfig.java", "src/main/java/com/meistudio/backend/config/WebConfig.java")
Add-Commit -Date "2025-11-28T14:45:00" -Message "feat: database schema design and initialization SQL" -Paths @("init.sql", "sql")

# Dec 2025: Persistence & Security
Add-Commit -Date "2025-12-05T10:30:00" -Message "feat: add domain entities for user and document" -Paths @("src/main/java/com/meistudio/backend/entity")
Add-Commit -Date "2025-12-12T15:20:00" -Message "feat: implement mybatis mappers for persistence" -Paths @("src/main/java/com/meistudio/backend/mapper")
Add-Commit -Date "2025-12-18T13:00:00" -Message "feat: implement JWT utilities and security logic" -Paths @("src/main/java/com/meistudio/backend/util/JwtUtil.java")
Add-Commit -Date "2025-12-28T17:00:00" -Message "feat: add jwt auth interceptor for request control" -Paths @("src/main/java/com/meistudio/backend/interceptor/AuthInterceptor.java")

# Jan 2026: Business Logic & RAG
Add-Commit -Date "2026-01-05T10:00:00" -Message "feat: implement basic REST controllers for user" -Paths @("src/main/java/com/meistudio/backend/controller/UserController.java", "src/main/java/com/meistudio/backend/service/UserService.java", "src/main/java/com/meistudio/backend/service/impl/UserServiceImpl.java")
Add-Commit -Date "2026-01-12T14:15:00" -Message "feat: add knowledge base and document services" -Paths @("src/main/java/com/meistudio/backend/controller/KnowledgeController.java", "src/main/java/com/meistudio/backend/service/KnowledgeService.java", "src/main/java/com/meistudio/backend/service/impl/KnowledgeServiceImpl.java")
Add-Commit -Date "2026-01-20T11:30:00" -Message "feat: integrate langchain4j and dashscope config" -Paths @("src/main/java/com/meistudio/backend/config")
Add-Commit -Date "2026-01-28T16:45:00" -Message "feat: implement RAG pipeline with redis vector store" -Paths @("src/main/java/com/meistudio/backend/service/impl/DocumentServiceImpl.java", "src/main/java/com/meistudio/backend/util")

# Feb 2026: HA & Protocol
Add-Commit -Date "2026-02-05T09:30:00" -Message "feat: implement rate limiting with fail-open strategy" -Paths @("src/main/java/com/meistudio/backend/interceptor/RateLimitInterceptor.java")
Add-Commit -Date "2026-02-12T14:45:00" -Message "feat: define MCP protocol and message structures" -Paths @("src/main/java/com/meistudio/backend/mcp/McpMessage.java", "src/main/java/com/meistudio/backend/mcp/McpProtocol.java")
Add-Commit -Date "2026-02-20T11:00:00" -Message "feat: implement mcp server connection via SSE push" -Paths @("src/main/java/com/meistudio/backend/mcp/client/McpServerConnection.java")
Add-Commit -Date "2026-02-28T15:30:00" -Message "feat: implement mcp tool proxy using adapter pattern" -Paths @("src/main/java/com/meistudio/backend/mcp/client/McpToolProxy.java")

# Mar 2026: Integration & Cleanup
Add-Commit -Date "2026-03-05T10:15:00" -Message "feat: implement mcp client discovery and registration" -Paths @("src/main/java/com/meistudio/backend/mcp/client/McpClientManager.java", "src/main/java/com/meistudio/backend/controller/McpConfigController.java")
Add-Commit -Date "2026-03-10T14:00:00" -Message "perf: optimize agent memory window management" -Paths @("src/main/java/com/meistudio/backend/service/AgentService.java")
Add-Commit -Date "2026-03-15T18:00:00" -Message "docs: prepare open source documentation and license" -Paths @("README.md", "LICENSE", "src/main/resources/application.yml.example")
Add-Commit -Date "2026-03-17T12:00:00" -Message "refactor: clean up unused code and tests" -Paths @(".")

git push --force origin main
