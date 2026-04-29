# AI智能体应用平台前端

这是一个基于Vue3开发的AI智能体应用平台，核心场景是 AI 生活导师对话与任务规划。

## 功能特点

- 💬 **AI生活导师**：围绕学习、职业、健康与时间管理提供可执行建议
- 🤖 **AI规划助手**：支持分步规划与工具调用执行

## 技术栈

- Vue3
- Vue Router
- Axios
- SSE (Server-Sent Events)

## 开发说明

### 环境要求

- Node.js >= 16.0.0
- npm >= 7.0.0

### 安装依赖

```bash
npm install
```

### 启动开发服务器

```bash
npm run dev
```

### 构建项目

```bash
npm run build
```

## 后端接口

项目依赖以下后端接口：

- `/api/ai/mentor/chat/sse` - AI生活导师聊天接口
- `/api/ai/manus/chat` - AI超级智能体聊天接口

后端服务默认运行在 `http://localhost:8123`

# Vue 3 + Vite

This template should help get you started developing with Vue 3 in Vite. The template uses Vue 3 `<script setup>` SFCs, check out the [script setup docs](https://v3.vuejs.org/api/sfc-script-setup.html#sfc-script-setup) to learn more.

Learn more about IDE Support for Vue in the [Vue Docs Scaling up Guide](https://vuejs.org/guide/scaling-up/tooling.html#ide-support).
