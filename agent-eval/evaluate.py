#!/usr/bin/env python3
"""
知域Agent 评估Harness

对Agent的三种模式（教练/规划/Agent）进行自动化评估，
覆盖结构完整性、工具选择准确性、关键词覆盖、Token效率四个维度。

用法:
    python evaluate.py                    # 跑全部用例
    python evaluate.py --mode coach       # 只跑教练模式
    python evaluate.py --case coach_001   # 跑指定用例
    python evaluate.py --output report.json  # 输出JSON报告

环境变量:
    AGENT_BASE_URL  线上地址，默认 https://www.xucanwei.top
    AGENT_AUTH_TOKEN  JWT Token（需要先登录获取）
"""

import json
import os
import re
import sys
import time
import uuid
from datetime import datetime
from typing import Any, Dict, List, Optional

import httpx

from metrics import (
    structure_completeness,
    tool_selection_accuracy,
    keyword_coverage,
    token_efficiency,
    rag_relevance,
)

# ---- 配置 ----
BASE_URL = os.environ.get("AGENT_BASE_URL", "https://www.xucanwei.top")
AUTH_TOKEN = os.environ.get("AGENT_AUTH_TOKEN", "")
API_KEY = os.environ.get("AGENT_API_KEY", "demo-local-key")

COACH_SSE_URL = f"{BASE_URL}/api/ai/mentor/chat/sse"
MANUS_SSE_URL = f"{BASE_URL}/api/ai/manus/chat"

HEADERS = {
    "Content-Type": "application/json",
}
if AUTH_TOKEN:
    HEADERS["Authorization"] = f"Bearer {AUTH_TOKEN}"

# ---- 工具函数 ----


def load_test_cases(path: str = "test_cases.json") -> List[Dict]:
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    return data.get("test_cases", data)


def parse_sse_response(response_text: str) -> Dict[str, Any]:
    """
    解析SSE流式响应，提取：
    - full_text: 完整回答文本
    - tool_calls: 工具调用列表 [{"name": "...", "args": {...}}, ...]
    - events: 所有事件的原始列表
    """
    result = {
        "full_text": "",
        "tool_calls": [],
        "events": [],
        "cancelled": False,
    }

    for line in response_text.split("\n"):
        line = line.strip()
        if not line.startswith("data:"):
            continue

        json_str = line[5:].strip()
        if not json_str:
            continue

        try:
            event = json.loads(json_str)
            result["events"].append(event)

            # 拼接文本内容
            content = event.get("content", "")
            if content:
                result["full_text"] += content

            # 收集工具调用
            tool_name = event.get("toolName")
            if tool_name:
                result["tool_calls"].append({
                    "name": tool_name,
                    "args": event.get("toolArgs", {}),
                    "result": event.get("toolResult", ""),
                })

            # 检查取消状态
            if event.get("eventType") == "cancelled":
                result["cancelled"] = True

        except json.JSONDecodeError:
            continue

    return result


def call_coach_api(message: str, mode: str = "COACH") -> Dict[str, Any]:
    """调用教练/规划模式API"""
    request_id = str(uuid.uuid4())
    chat_id = f"eval_{datetime.now().strftime('%Y%m%d%H%M%S')}"

    payload = {
        "message": message,
        "chatId": chat_id,
        "mode": mode,
        "requestId": request_id,
    }

    req_headers = dict(HEADERS)
    req_headers["X-API-Key"] = API_KEY

    try:
        with httpx.Client(timeout=120.0, follow_redirects=True) as client:
            response = client.post(
                COACH_SSE_URL,
                json=payload,
                headers=req_headers,
            )
            response.raise_for_status()
            parsed = parse_sse_response(response.text)
            parsed["request_id"] = request_id
            parsed["http_status"] = response.status_code
            return parsed
    except httpx.HTTPError as e:
        return {"error": str(e), "full_text": "", "tool_calls": [], "events": []}


def call_agent_api(message: str) -> Dict[str, Any]:
    """调用Agent模式API"""
    request_id = str(uuid.uuid4())
    chat_id = f"eval_agent_{datetime.now().strftime('%Y%m%d%H%M%S')}"

    params = {
        "message": message,
        "chatId": chat_id,
        "requestId": request_id,
    }

    req_headers = {"Content-Type": "application/json", "X-API-Key": API_KEY}

    try:
        with httpx.Client(timeout=180.0, follow_redirects=True) as client:
            response = client.get(
                MANUS_SSE_URL,
                params=params,
                headers=req_headers,
            )
            response.raise_for_status()
            parsed = parse_sse_response(response.text)
            parsed["request_id"] = request_id
            parsed["http_status"] = response.status_code
            return parsed
    except httpx.HTTPError as e:
        return {"error": str(e), "full_text": "", "tool_calls": [], "events": []}


# ---- 模拟响应数据（dry-run模式用） ----

MOCK_RESPONSES = {
    "coach": {
        "full_text": """## 结论

根据你的情况，拖延行为可能源于任务过大导致的心理压力和缺乏即时反馈机制。

## 分步行动

1. **任务分解**：将大目标拆分为15-25分钟可完成的小单元（番茄钟法）
2. **环境优化**：移除手机等干扰源，创建专注工作区
3. **即时奖励**：每完成一个番茄钟，给自己5分钟休息或小奖励
4. **公开承诺**：告知朋友或同事你的计划，利用社交压力对抗拖延

## 风险提醒

- 建议先从最轻松的任务开始，不要一上来就挑战最难的事，容易受挫放弃
- 如果拖延伴随严重焦虑或抑郁情绪，建议同时寻求专业心理咨询
- 番茄钟法对创意类工作的适用性有限，可能需要调整为90分钟深度工作块

## 复盘问题

- 这周哪个任务你拖延最久？是什么触发了回避行为？
- 分解后的最小第一步是什么？你可以在接下来25分钟内完成吗？
- 一周后回顾：哪种方法对你最有效？""",
        "tool_calls": [],
        "events": [],
    },
    "planner": {
        "full_text": """## 一、第一阶段：基础入门（第1-3周）

**学习目标**：掌握Python基础语法和常用数据结构
- Week1：变量、数据类型、条件判断、循环
- Week2：列表/字典/集合、函数定义、文件操作
- Week3：面向对象基础、异常处理、模块导入

## 二、第二阶段：数据分析核心技能（第4-8周）

**学习目标**：熟练使用pandas和matplotlib
- Week4-5：pandas数据处理（读取/清洗/聚合/透视表）
- Week6-7：matplotlib/seaborn可视化（折线图/柱状图/热力图）
- Week8：实战项目——用Kaggle数据集做完整分析

## 三、第三阶段：项目实战（第9-12周）

**学习目标**：独立完成一个数据分析项目
- 选题建议：爬取招聘网站数据，分析目标岗位的技能要求分布
- 产出要求：完整报告（含数据清洗过程、可视化图表、结论建议）

## 四、风险提醒

- 零基础学Python最大的坑是"只听课不写代码"，建议每天至少敲1小时
- pandas的API比较庞大，别想着全记住，用的时候查文档就行
- 数据分析的难点不是Python本身，而是统计思维——推荐同步看《赤裸裸的统计学》""",
        "tool_calls": [],
        "events": [],
    },
    "agent": {
        "full_text": "根据搜索结果，2024年AI应用开发有以下几个关键趋势：\n\n1. AI Agent的爆发式增长，越来越多的企业开始将AI Agent集成到业务流程中\n2. 多模态AI应用成为主流，文本+图像+语音的融合应用大幅增加\n3. 开源模型性能逼近闭源模型，降低了AI应用开发的门槛和成本",
        "tool_calls": [
            {"name": "WebSearchTool", "args": {"query": "2024 AI应用开发趋势"}, "result": "搜索结果摘要"},
            {"name": "WebScrapingTool", "args": {"url": "https://example.com/ai-trends"}, "result": "网页内容摘要"},
        ],
        "events": [],
    },
}


def evaluate_case(case: Dict) -> Dict[str, Any]:
    """评估单条测试用例（调真实API）"""
    case_id = case["id"]
    mode = case["mode"]
    query = case["query"]
    expect_tools = case.get("expect_tools", [])
    expect_structure = case.get("expect_structure", [])
    expect_keywords = case.get("expect_keywords", [])
    description = case.get("description", "")

    print(f"\n{'='*60}")
    print(f"[{case_id}] {description}")
    print(f"  模式: {mode}  |  问题: {query[:50]}...")

    start_time = time.time()
    if mode == "agent":
        result = call_agent_api(query)
    else:
        api_mode = "PLANNER" if mode == "planner" else "COACH"
        result = call_coach_api(query, api_mode)

    elapsed = round(time.time() - start_time, 2)

    if "error" in result and result.get("error"):
        print(f"  [FAIL] API调用失败: {result['error']}")
        return {
            "case_id": case_id, "mode": mode, "description": description,
            "error": result["error"], "elapsed_sec": elapsed, "metrics": {},
        }

    full_text = result.get("full_text", "")
    tool_calls = result.get("tool_calls", [])
    actual_tool_names = [tc["name"] for tc in tool_calls]

    print(f"  响应长度: {len(full_text)} 字符  |  耗时: {elapsed}s")
    if actual_tool_names:
        print(f"  调用工具: {actual_tool_names}")

    metrics_result = {}
    if expect_structure:
        metrics_result["structure"] = structure_completeness(full_text, expect_structure)
        print(f"  结构完整性: {metrics_result['structure']['score']:.0%}"
              f"  (缺失: {metrics_result['structure']['missing']})")

    metrics_result["tools"] = tool_selection_accuracy(actual_tool_names, expect_tools)
    if expect_tools:
        print(f"  工具准确性: F1={metrics_result['tools']['score']:.0%}"
              f"  (遗漏: {metrics_result['tools']['missed']}, 多余: {metrics_result['tools']['extra']})")

    if expect_keywords:
        metrics_result["keywords"] = keyword_coverage(full_text, expect_keywords)
        print(f"  关键词覆盖: {metrics_result['keywords']['score']:.0%}"
              f"  (缺失: {metrics_result['keywords']['missing']})")

    metrics_result["token_eff"] = token_efficiency(full_text)
    te = metrics_result["token_eff"]
    print(f"  Token效率: {te.get('total_tokens', '?')} tokens"
          f"  ({te.get('chars_per_token', '?')} chars/token)")

    metrics_result["rag"] = rag_relevance(query, full_text)
    print(f"  RAG相关性(启发式): {metrics_result['rag']['score']:.0%}")

    return {
        "case_id": case_id, "mode": mode, "description": description,
        "query": query,
        "response_preview": full_text[:200] + "..." if len(full_text) > 200 else full_text,
        "elapsed_sec": elapsed, "tools_used": actual_tool_names,
        "metrics": metrics_result,
        "events_count": len(result.get("events", [])),
    }


def dry_run_evaluate(case: Dict) -> Dict[str, Any]:
    """使用模拟数据评估（不调API）"""
    case_id = case["id"]
    mode = case["mode"]
    query = case["query"]
    description = case.get("description", "")

    mock_key = "coach"
    if mode == "planner":
        mock_key = "planner"
    elif mode == "agent":
        mock_key = "agent"

    mock = MOCK_RESPONSES.get(mock_key, MOCK_RESPONSES["coach"])

    print(f"\n{'='*60}")
    print(f"[{case_id}] {description}  [DRY-RUN]")
    print(f"  模式: {mode}  |  问题: {query[:50]}...")

    full_text = mock["full_text"]
    actual_tool_names = [tc["name"] for tc in mock.get("tool_calls", [])]
    expect_tools = case.get("expect_tools", [])
    expect_structure = case.get("expect_structure", [])
    expect_keywords = case.get("expect_keywords", [])

    print(f"  响应长度: {len(full_text)} 字符  |  (dry-run模式，模拟数据)")
    if actual_tool_names:
        print(f"  模拟工具调用: {actual_tool_names}")

    metrics_result = {}
    if expect_structure:
        metrics_result["structure"] = structure_completeness(full_text, expect_structure)
        print(f"  结构完整性: {metrics_result['structure']['score']:.0%}  "
              f"(缺失: {metrics_result['structure']['missing']})")

    metrics_result["tools"] = tool_selection_accuracy(actual_tool_names, expect_tools)
    if expect_tools:
        print(f"  工具准确性: F1={metrics_result['tools']['score']:.0%}  "
              f"(遗漏: {metrics_result['tools']['missed']}, 多余: {metrics_result['tools']['extra']})")

    if expect_keywords:
        metrics_result["keywords"] = keyword_coverage(full_text, expect_keywords)
        print(f"  关键词覆盖: {metrics_result['keywords']['score']:.0%}  "
              f"(缺失: {metrics_result['keywords']['missing']})")

    metrics_result["token_eff"] = token_efficiency(full_text)
    te = metrics_result["token_eff"]
    print(f"  Token效率: {te.get('total_tokens', '?')} tokens  "
          f"({te.get('chars_per_token', '?')} chars/token)")
    metrics_result["rag"] = rag_relevance(query, full_text)
    print(f"  RAG相关性(启发式): {metrics_result['rag']['score']:.0%}")

    return {
        "case_id": case_id,
        "mode": mode,
        "description": description,
        "query": query,
        "response_preview": full_text[:200] + "..." if len(full_text) > 200 else full_text,
        "elapsed_sec": 0,
        "tools_used": actual_tool_names,
        "metrics": metrics_result,
        "events_count": 0,
    }
    """评估单条测试用例"""
    case_id = case["id"]
    mode = case["mode"]
    query = case["query"]
    expect_tools = case.get("expect_tools", [])
    expect_structure = case.get("expect_structure", [])
    expect_keywords = case.get("expect_keywords", [])
    description = case.get("description", "")

    print(f"\n{'='*60}")
    print(f"[{case_id}] {description}")
    print(f"  模式: {mode}  |  问题: {query[:50]}...")

    # 调API
    start_time = time.time()
    if mode == "agent":
        result = call_agent_api(query)
    else:
        api_mode = "PLANNER" if mode == "planner" else "COACH"
        result = call_coach_api(query, api_mode)

    elapsed = round(time.time() - start_time, 2)

    if "error" in result and result.get("error"):
        print(f"  [FAIL] API调用失败: {result['error']}")
        return {
            "case_id": case_id,
            "mode": mode,
            "description": description,
            "error": result["error"],
            "elapsed_sec": elapsed,
            "metrics": {},
        }

    full_text = result.get("full_text", "")
    tool_calls = result.get("tool_calls", [])
    actual_tool_names = [tc["name"] for tc in tool_calls]

    print(f"  响应长度: {len(full_text)} 字符  |  耗时: {elapsed}s")
    if actual_tool_names:
        print(f"  调用工具: {actual_tool_names}")

    # 跑各项指标
    metrics = {}

    # 1. 结构完整性
    if expect_structure:
        metrics["structure"] = structure_completeness(full_text, expect_structure)
        print(f"  结构完整性: {metrics['structure']['score']:.0%}  "
              f"(缺失: {metrics['structure']['missing']})")

    # 2. 工具选择准确性
    metrics["tools"] = tool_selection_accuracy(actual_tool_names, expect_tools)
    if expect_tools:
        print(f"  工具准确性: F1={metrics['tools']['score']:.0%}  "
              f"(遗漏: {metrics['tools']['missed']}, 多余: {metrics['tools']['extra']})")

    # 3. 关键词覆盖
    if expect_keywords:
        metrics["keywords"] = keyword_coverage(full_text, expect_keywords)
        print(f"  关键词覆盖: {metrics['keywords']['score']:.0%}  "
              f"(缺失: {metrics['keywords']['missing']})")

    # 4. Token效率
    metrics["token_eff"] = token_efficiency(full_text)
    print(f"  Token效率: {metrics['token_eff']['estimated_tokens']} 估算tokens  "
          f"({metrics['token_eff']['chars_per_token']} chars/token)")

    # 5. RAG相关性（如果有检索上下文）
    metrics["rag"] = rag_relevance(query, full_text)

    return {
        "case_id": case_id,
        "mode": mode,
        "description": description,
        "query": query,
        "response_preview": full_text[:200] + "..." if len(full_text) > 200 else full_text,
        "elapsed_sec": elapsed,
        "tools_used": actual_tool_names,
        "metrics": metrics,
        "events_count": len(result.get("events", [])),
    }


def summarize(results: List[Dict]) -> Dict[str, Any]:
    """汇总所有用例的评估结果"""
    valid_results = [r for r in results if "error" not in r or not r.get("error")]

    if not valid_results:
        return {"total": len(results), "valid": 0, "message": "所有用例均失败"}

    # 按模式分组
    by_mode: Dict[str, List] = {}
    for r in valid_results:
        mode = r["mode"]
        by_mode.setdefault(mode, []).append(r)

    # 各模式汇总
    mode_summary = {}
    for mode, cases in by_mode.items():
        structure_scores = [
            c["metrics"].get("structure", {}).get("score", 0) for c in cases
        ]
        tool_scores = [
            c["metrics"].get("tools", {}).get("score", 0) for c in cases
        ]
        keyword_scores = [
            c["metrics"].get("keywords", {}).get("score", 0) for c in cases
        ]
        avg_structure = sum(structure_scores) / len(structure_scores) if structure_scores else 0
        avg_tools = sum(tool_scores) / len(tool_scores) if tool_scores else 0
        avg_keywords = sum(keyword_scores) / len(keyword_scores) if keyword_scores else 0

        mode_summary[mode] = {
            "count": len(cases),
            "avg_structure_score": round(avg_structure, 3),
            "avg_tool_score": round(avg_tools, 3),
            "avg_keyword_score": round(avg_keywords, 3),
            "avg_elapsed_sec": round(
                sum(c["elapsed_sec"] for c in cases) / len(cases), 2
            ),
        }

    # 总体均值
    all_structure = [c["metrics"].get("structure", {}).get("score", 0) for c in valid_results]
    all_tools = [c["metrics"].get("tools", {}).get("score", 0) for c in valid_results]

    summary = {
        "evaluated_at": datetime.now().isoformat(),
        "base_url": BASE_URL,
        "total_cases": len(results),
        "valid_cases": len(valid_results),
        "failed_cases": len(results) - len(valid_results),
        "overall_avg_structure_score": round(sum(all_structure) / max(len(all_structure), 1), 3),
        "overall_avg_tool_score": round(sum(all_tools) / max(len(all_tools), 1), 3),
        "by_mode": mode_summary,
        "per_case": valid_results,
    }

    return summary


def print_summary(summary: Dict):
    """终端友好的汇总报告"""
    print(f"\n{'='*60}")
    print("  知域Agent 评估报告")
    print(f"{'='*60}")
    print(f"  评估时间: {summary.get('evaluated_at', 'N/A')}")
    print(f"  目标地址: {summary.get('base_url', BASE_URL)}")
    print(f"  总用例: {summary.get('total_cases', 0)}  |  "
          f"通过: {summary.get('valid_cases', 0)}  |  "
          f"失败: {summary.get('failed_cases', 0)}")
    print(f"  整体结构分: {summary.get('overall_avg_structure_score', 0):.0%}")
    print(f"  整体工具分: {summary.get('overall_avg_tool_score', 0):.0%}")

    if summary.get("message"):
        print(f"\n  {summary['message']}")
        return

    for mode, ms in summary.get("by_mode", {}).items():
        print(f"  [{mode.upper()}模式] ({ms['count']}条用例)")
        print(f"    结构完整性: {ms['avg_structure_score']:.0%}")
        print(f"    工具准确性:   {ms['avg_tool_score']:.0%}")
        print(f"    关键词覆盖:   {ms['avg_keyword_score']:.0%}")
        print(f"    平均耗时:     {ms['avg_elapsed_sec']}s")
        print()

    # 逐条详情
    for case in summary.get("per_case", []):
        errors = []
        metrics = case.get("metrics", {})
        structure = metrics.get("structure", {})
        tools = metrics.get("tools", {})

        if structure.get("score", 1.0) < 0.5:
            errors.append(f"结构不完整(缺失:{structure.get('missing', [])})")
        if tools.get("score", 1.0) < 0.5:
            errors.append(f"工具不准(遗漏:{tools.get('missed', [])})")

        status = "[OK]" if not errors else "[!!]"
        print(f"  {status} {case['case_id']}: {case['description'][:40]}")
        for e in errors:
            print(f"       {e}")


def main():
    import argparse

    parser = argparse.ArgumentParser(description="知域Agent评估Harness")
    parser.add_argument("--mode", choices=["coach", "planner", "agent"],
                        help="只跑指定模式")
    parser.add_argument("--case", type=str,
                        help="只跑指定用例ID")
    parser.add_argument("--output", type=str,
                        help="输出JSON报告文件路径")
    parser.add_argument("--test-cases", type=str, default="test_cases.json",
                        help="测试用例文件路径")
    parser.add_argument("--dry-run", action="store_true",
                        help="使用模拟数据验证评估逻辑（无需启动应用）")
    parser.add_argument("--local", action="store_true",
                        help="调用本地应用（localhost:8123）")

    global BASE_URL, COACH_SSE_URL, MANUS_SSE_URL

    args = parser.parse_args()

    if args.local:
        BASE_URL = "http://localhost:8123"
        COACH_SSE_URL = f"{BASE_URL}/api/ai/mentor/chat/sse"
        MANUS_SSE_URL = f"{BASE_URL}/api/ai/manus/chat"
        print(f"[local模式] 目标: {BASE_URL}")

    # 加载用例
    all_cases = load_test_cases(args.test_cases)
    print(f"加载了 {len(all_cases)} 条测试用例")

    if args.case:
        all_cases = [c for c in all_cases if c["id"] == args.case]
    elif args.mode:
        all_cases = [c for c in all_cases if c["mode"] == args.mode]
        print(f"过滤后：{len(all_cases)} 条 ({args.mode}模式)")

    if not all_cases:
        print("没有匹配的测试用例")
        sys.exit(1)

    if args.dry_run:
        print("[dry-run模式] 使用模拟数据，验证评估指标逻辑\n")
    elif not AUTH_TOKEN:
        print("\n[WARNING] 未设置 AGENT_AUTH_TOKEN 环境变量")
        print("  获取Token: POST {}/api/auth/login".format(BASE_URL))
        print("  或用 --dry-run 模式先验证评估逻辑\n")

    # 跑评估
    results = []
    for i, case in enumerate(all_cases, 1):
        print(f"\n[{i}/{len(all_cases)}] 评估中...", end=" ", flush=True)
        if args.dry_run:
            result = dry_run_evaluate(case)
        else:
            result = evaluate_case(case)
        results.append(result)

    # 汇总
    summary = summarize(results)
    print_summary(summary)

    # 输出JSON
    if args.output:
        with open(args.output, "w", encoding="utf-8") as f:
            json.dump(summary, f, ensure_ascii=False, indent=2)
        print(f"\n评估报告已保存至: {args.output}")

    # 返回非零退出码表示有失败
    failed = summary.get("failed_cases", 0)
    if failed > 0:
        sys.exit(1)


if __name__ == "__main__":
    main()
