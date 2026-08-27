# 冷启动优化方案

## 目标
解决微信云托管在「定时扩容时段之外」访问时冷启动慢的问题。方向已确认：**不额外付费**，通过代码层缩短应用启动时间（保持缩到 0 的省钱策略不变）。

## 根因
1. **平台冷启动**（缩到 0 的必然代价）——本次不改，保持省钱。
2. **应用启动慢**，发现三个实打实的浪费点：
   - `demo/invoke/SpringAiAiInvoke.java`：`@Component` 实现 `CommandLineRunner`，启动时**同步调用 DashScope 大模型**发一句问候语。遗留 demo，白白阻塞几秒做真实 LLM 网络请求（旁边 `OllamaAiInvoke` 早已注释，唯独它没删）。
   - **MCP stdio 两个 server 启动时 eager 拉起**：`image-search-mcp-server` 是 `java -jar`（额外拉起第二个 JVM）；`amap-maps` 是 `npx -y @amap/amap-maps-mcp-server`，全新容器冷启动时 npx 每次都要**重新下载 npm 包**，开销 10–20s。
   - **JVM 默认启动参数未优化**（无分层编译、无 GC 调优、JMX 开启）。

## 改动清单

### 1. 注释 demo 的 LLM 调用（消除 ~3–5s）
- 文件：`src/main/java/com/xcw/aiagentbackend/demo/invoke/SpringAiAiInvoke.java`
- 改法：注释掉 `@Component` 注解（与 `OllamaAiInvoke` 一致），保留类体。

### 2. JVM 启动参数优化（减少 Spring 启动时间）
- 文件：`docker/supervisord.conf`
- 改法：`backend` 的 command 改为
  `java -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Dspring.jmx.enabled=false -jar /app/app.jar --spring.profiles.active=prod`
- 说明：`TieredStopAtLevel=1` 跳过 C2 编译加速启动；`UseSerialGC` 适合少核容器；关闭 JMX 省初始化开销。

### 3. 预装 amap npm 包（消除冷启动 npx 下载 ~10–20s）
- 文件：`Dockerfile`、`src/main/resources/mcp-servers-prod.json`
- 改法：
  - `Dockerfile` Stage4 增加 `RUN npm install -g @amap/amap-maps-mcp-server`
  - `mcp-servers-prod.json` 里 `amap-maps` 的 command 由 `npx -y @amap/amap-maps-mcp-server` 改为直接调用全局二进制（或用 `npx --no-install` 强制走本地）

### 4. MCP 懒加载（收益有限，先验证）
- 文件：`src/main/resources/application-prod.yml`
- 改法：增加 `spring.ai.mcp.client.initialized: false`
- 说明：Spring AI 1.0.0-M6 中该开关（默认 `true`）只延迟协议握手，**不延迟 stdio 子进程 spawn**，收益有限。完整懒加载需排除自动配置 + 自定义 `@Lazy` provider，风险中等。**先做 1–3 并实测启动时间，若 MCP 仍为主要瓶颈再迭代。**

## 验证
1. `mvn clean package -DskipTests` 确认编译通过。
2. 本地起后端观察启动日志耗时（重点看 `SpringAiAiInvoke` 是否不再执行、MCP 连接耗时）。
3. 构建镜像后对比优化前后冷启动时间。

## 明确不做
- 平台层常驻/扩容调整（保持省钱策略）。
- 完整 MCP 懒加载（作为后续可选迭代，本轮不强行引入风险）。
