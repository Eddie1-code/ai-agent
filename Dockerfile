# ================================================================
# AI Agent — 多阶段 Docker 构建
# 微信云托管 / 腾讯云容器服务 适用
# ================================================================

# -------------------------------
# Stage 1: 构建 MCP Server JAR
# -------------------------------
FROM maven:3.9-eclipse-temurin-21 AS mcp-build
WORKDIR /build/mcp
COPY image-search-mcp-server/pom.xml ./
RUN mvn dependency:go-offline -q || true
COPY image-search-mcp-server/src ./src
RUN mvn clean package -DskipTests -q

# -------------------------------
# Stage 2: 构建主后端 JAR
# -------------------------------
FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /build/backend
COPY pom.xml ./
RUN mvn dependency:go-offline -q || true
COPY src ./src
RUN mvn clean package -DskipTests -q

# -------------------------------
# Stage 3: 构建前端
# -------------------------------
FROM node:20-alpine AS frontend-build
WORKDIR /app
COPY ai-agent-frontend/package.json ai-agent-frontend/package-lock.json* ./
RUN npm install --registry=https://registry.npmmirror.com
COPY ai-agent-frontend/ ./
RUN npm run build

# -------------------------------
# Stage 4: 最终运行镜像
# -------------------------------
FROM eclipse-temurin:21-jre

# nginx + supervisor + Node.js（高德 MCP 需要 npx）
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        nginx \
        supervisor \
        curl \
        gnupg \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/* \
    && npm config set registry https://registry.npmmirror.com

WORKDIR /app

# 复制 JAR
COPY --from=backend-build /build/backend/target/*.jar /app/app.jar
COPY --from=mcp-build /build/mcp/target/*.jar /app/image-search-mcp-server.jar

# 复制前端产物
COPY --from=frontend-build /app/dist /usr/share/nginx/html

# 复制 nginx 配置
COPY docker/nginx.conf /etc/nginx/sites-available/default

# 复制 supervisor 配置
COPY docker/supervisord.conf /etc/supervisor/conf.d/supervisord.conf

# 运行时目录
RUN mkdir -p /app/tmp/file /app/tmp/download /app/tmp/generated-images

EXPOSE 80

CMD ["/usr/bin/supervisord", "-c", "/etc/supervisor/conf.d/supervisord.conf"]
