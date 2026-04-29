# AI Agent Backend 使用说明

本文档补充后端改造后的核心调用方式：`JWT 登录鉴权` 与 `API Key 调用` 可并行使用。

## 1. 环境变量

建议先配置以下环境变量（本地可按需最小配置）：

- `APP_JWT_SECRET`：JWT 签名密钥（至少 32 字符）
- `APP_JWT_EXPIRE_SECONDS`：JWT 过期秒数（默认 86400）
- `DASHSCOPE_API_KEY`：模型调用密钥（不配置会导致聊天模型不可用）
- `TENCENT_AIART_ENABLED`：是否开启腾讯生图（`true/false`）
- `TENCENT_SECRET_ID`：腾讯云 SecretId
- `TENCENT_SECRET_KEY`：腾讯云 SecretKey
- `TENCENT_AIART_REGION`：腾讯生图地域（默认 `ap-guangzhou`）
- `TENCENT_AIART_DEFAULT_RESOLUTION`：默认分辨率（默认 `1024:1024`）

## 1.1 启动前快速检查

- 本地仅文本对话：至少保证 `DASHSCOPE_API_KEY` 有值（`application-local.yml` 已提供占位值用于避免启动失败）。
- 需要真实生图：必须设置 `TENCENT_SECRET_ID` 和 `TENCENT_SECRET_KEY`，否则会返回“腾讯云密钥未配置”提示。

## 2. 登录并获取 JWT

### 2.1 注册

```bash
curl -X POST "http://localhost:8123/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"demo_user\",\"password\":\"demo_pass_123\"}"
```

### 2.2 登录

```bash
curl -X POST "http://localhost:8123/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"demo_user\",\"password\":\"demo_pass_123\"}"
```

返回中 `data.token` 即 JWT。

### 2.3 使用 JWT 调用受保护接口

```bash
curl "http://localhost:8123/api/task/latest?limit=10" \
  -H "Authorization: Bearer <your_jwt_token>"
```

## 3. 使用 API Key 调用（第三方接入）

默认本地种子 key 为 `demo-local-key`（可在配置或数据库中调整）。

### 3.1 查询任务列表

```bash
curl "http://localhost:8123/api/task/latest?limit=10" \
  -H "X-API-Key: demo-local-key"
```

### 3.2 停止流式输出

```bash
curl -X POST "http://localhost:8123/api/ai/mentor/chat/stop" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: demo-local-key" \
  -d "{\"requestId\":\"<your_request_id>\"}"
```

## 4. 鉴权优先级与路由策略

- 受保护路由：`/api/ai/**`、`/api/task/**`
- 白名单：`/api/auth/**`、`/api/health/**`、Swagger 文档路径
- 同时携带 JWT 与 API Key 时，已认证上下文优先沿用（避免重复覆盖）

## 5. 计费与用量

当前版本为 MVP：按“调用次数”计费。

- `api_key_account.used_count`：累计调用次数
- `api_key_usage`：记录每次调用的 URI、方法、状态码、耗时、requestId

## 6. 常见排查

- 401：未携带有效 JWT 或 API Key
- 403：身份已认证但无权限
- 429：API Key 触发限流
- SSE 停止无效：确认 `requestId` 与当前会话一致
