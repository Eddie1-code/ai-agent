# AI 生活导师部署指南（微信云托管）

## 1. 架构

- 前端：`ai-agent-frontend`（Vite 构建静态资源）
- 后端：Spring Boot（本项目 `Dockerfile`）
- 数据库：腾讯云 MySQL（CDB）
- 缓存：腾讯云 Redis
- 大模型与知识库：百炼 DashScope + 百炼知识库

## 2. 关键环境变量

后端容器环境变量建议如下：

- `SPRING_PROFILES_ACTIVE=prod`
- `APP_DATASOURCE_URL=jdbc:mysql://<mysql-host>:3306/ai_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai`
- `APP_DATASOURCE_USERNAME=<mysql-user>`
- `APP_DATASOURCE_PASSWORD=<mysql-pass>`
- `SPRING_DATA_REDIS_HOST=<redis-host>`
- `SPRING_DATA_REDIS_PORT=6379`
- `SPRING_DATA_REDIS_PASSWORD=<redis-pass>`
- `SPRING_DATA_REDIS_DATABASE=0`
- `APP_JWT_SECRET=<至少32位随机串>`
- `APP_JWT_EXPIRE_SECONDS=86400`
- `SPRING_AI_DASHSCOPE_API_KEY=<百炼API Key>`
- `APP_RAG_CLOUD_KNOWLEDGE_INDEX=AI Agent生活导师`
- `AMAP_MAPS_API_KEY=<高德Key>`
- `SEARCH_API_API_KEY=<搜索API Key>`

## 3. 后端镜像构建与推送

在项目根目录执行：

```bash
docker build -t <registry>/ai-agent-backend:latest .
docker push <registry>/ai-agent-backend:latest
```

## 4. 微信云托管配置要点

1. 创建服务，选择容器镜像部署模式。
2. 镜像地址填 `<registry>/ai-agent-backend:latest`。
3. 容器端口设置为 `8123`。
4. 健康检查建议路径：`/api/health`。
5. 注入上方环境变量。
6. 配置出站网络，确保可访问：
   - 腾讯云 MySQL/Redis 内网地址
   - 百炼 API
   - 高德 API

## 5. 前端部署

前端可选两种：

- 方式A：微信云托管静态服务（推荐统一）
- 方式B：COS 静态网站托管

本地构建：

```bash
cd ai-agent-frontend
npm install
npm run build
```

将 `dist` 上传到静态托管服务，反向代理 `/api` 到后端服务域名。

## 6. 首次上线检查清单

- [ ] 能访问 `/api/health`
- [ ] 登录/注册成功
- [ ] `/chat/sessions` 可创建会话
- [ ] SSE 聊天可持续返回并能停止
- [ ] 个人中心资料更新、改密正常
- [ ] 云端 RAG 命中知识库（检查日志与回答内容）
