"""
Agent 评估指标实现

四个评估维度：
1. structure_completeness - 回答结构完整性（规则匹配）
2. tool_selection_accuracy - 工具选择准确性
3. rag_relevance - RAG检索相关性（RAGAS库）
4. token_efficiency - Token使用效率
"""

import re
from typing import List, Optional, Dict, Any


def structure_completeness(
    response_text: str,
    expected_sections: List[str]
) -> Dict[str, Any]:
    """
    检查回答是否包含预期的结构段落。
    教练模式应包含：结论、分步行动、风险提醒、复盘问题。
    返回命中率和缺失的段落。
    """
    if not response_text or not expected_sections:
        return {"score": 0.0, "found": [], "missing": expected_sections}

    found = []
    missing = []

    for section in expected_sections:
        # 检查段落关键词是否存在，支持多种标题格式
        pattern = re.compile(
            rf"(?:^|\n)\s*(?:#+\s*)?.*?{re.escape(section)}.*?(?:\n|$)",
            re.IGNORECASE
        )

        # 也尝试更宽松的匹配：正文中出现关键词即算命中
        if pattern.search(response_text) or section in response_text:
            found.append(section)
        else:
            missing.append(section)

    score = len(found) / len(expected_sections) if expected_sections else 1.0
    return {
        "score": round(score, 3),
        "found": found,
        "missing": missing,
    }


def tool_selection_accuracy(
    actual_tools: List[str],
    expected_tools: List[str]
) -> Dict[str, Any]:
    """
    评估Agent工具选择的准确性。
    - precision: 实际调用的工具中有多少是需要的
    - recall: 需要的工具中有多少被调用了
    - 如果没有期望工具且实际也没调用，视为完美匹配
    """
    if not expected_tools and not actual_tools:
        return {"score": 1.0, "precision": 1.0, "recall": 1.0,
                "expected": [], "actual": [], "missed": [], "extra": []}

    if not expected_tools:
        return {"score": 1.0 if not actual_tools else 0.5,
                "precision": 0.0, "recall": 1.0,
                "expected": [], "actual": actual_tools,
                "missed": [], "extra": actual_tools}

    actual_set = set(actual_tools)
    expected_set = set(expected_tools)

    tp = len(actual_set & expected_set)
    fp = len(actual_set - expected_set)
    fn = len(expected_set - actual_set)

    precision = tp / len(actual_set) if actual_set else 0.0
    recall = tp / len(expected_set) if expected_set else 1.0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0

    return {
        "score": round(f1, 3),
        "precision": round(precision, 3),
        "recall": round(recall, 3),
        "expected": list(expected_set),
        "actual": list(actual_set),
        "missed": list(expected_set - actual_set),
        "extra": list(actual_set - expected_set),
    }


def keyword_coverage(
    response_text: str,
    expected_keywords: List[str]
) -> Dict[str, Any]:
    """
    检查回答中是否覆盖了预期关键词。
    返回覆盖率。
    """
    if not expected_keywords:
        return {"score": 1.0, "found": [], "missing": []}

    found = []
    missing = []
    for kw in expected_keywords:
        if kw.lower() in response_text.lower():
            found.append(kw)
        else:
            missing.append(kw)

    score = len(found) / len(expected_keywords)
    return {
        "score": round(score, 3),
        "found": found,
        "missing": missing,
    }


def token_efficiency(
    response_text: str,
    token_usage: Optional[Dict[str, int]] = None
) -> Dict[str, Any]:
    """
    评估Token使用效率。
    - 如果提供了token_usage（来自API响应），用实际数据
    - 否则基于字符数估算（中文约1.5字符/token，英文约4字符/token）
    """
    if not response_text:
        return {"score": 0.0, "char_count": 0, "estimated_tokens": 0,
                "chars_per_token": 0}

    char_count = len(response_text)

    if token_usage:
        total_tokens = token_usage.get("total_tokens", 0)
        prompt_tokens = token_usage.get("prompt_tokens", 0)
        completion_tokens = token_usage.get("completion_tokens", 0)
    else:
        # 粗略估算：中英文混合场景约2.5字符/token
        prompt_tokens = 0
        completion_tokens = int(char_count / 2.5)
        total_tokens = completion_tokens

    # 效率分：有效输出字符数 / 总token数，数值越高越好
    # 正常范围：2-4 chars/token 为合理
    chars_per_token = char_count / total_tokens if total_tokens > 0 else 0
    # 归一化到0-1，设定4 chars/token为满分
    score = min(chars_per_token / 4.0, 1.0)

    return {
        "score": round(score, 3),
        "char_count": char_count,
        "total_tokens": total_tokens,
        "prompt_tokens": prompt_tokens,
        "completion_tokens": completion_tokens,
        "chars_per_token": round(chars_per_token, 1),
    }


def rag_relevance(
    query: str,
    response_text: str,
    retrieved_contexts: Optional[List[str]] = None
) -> Dict[str, Any]:
    """
    评估RAG检索的相关性。
    如果有retrieved_contexts，尝试用RAGAS做精确评估；
    否则基于回答内容做启发式相关性判断。
    """
    if not retrieved_contexts:
        # 无检索上下文时，做启发式评估
        # 检查回答是否直接回应了问题主题
        query_keywords = set(query[:30])  # 取问题前30个字符作为主题锚
        response_start = response_text[:100] if response_text else ""

        # 简单判断：回答开头是否呼应了问题
        anchors_found = sum(1 for c in query_keywords if c in response_start)
        score = min(anchors_found / max(len(query_keywords), 1), 1.0)

        return {
            "score": round(score, 2),
            "method": "heuristic",
            "note": "无检索上下文数据，使用启发式评估。接入RAGAS需提供retrieved_contexts参数。"
        }

    # 有检索上下文时，使用RAGAS进行评估
    try:
        from ragas import evaluate
        from ragas.metrics import faithfulness, context_relevancy
        from langchain_core.documents import Document

        docs = [Document(page_content=ctx) for ctx in retrieved_contexts]

        dataset = {
            "question": [query],
            "answer": [response_text],
            "contexts": [[d.page_content for d in docs]],
        }

        result = evaluate(
            dataset=dataset,
            metrics=[faithfulness, context_relevancy],
        )

        return {
            "score": round(float(result.get("faithfulness", 0)), 3),
            "faithfulness": round(float(result.get("faithfulness", 0)), 3),
            "context_relevancy": round(float(result.get("context_relevancy", 0)), 3),
            "method": "ragas",
        }
    except ImportError:
        return {
            "score": 0.0,
            "method": "error",
            "note": "RAGAS未安装或导入失败，请运行: pip install ragas"
        }
    except Exception as e:
        return {
            "score": 0.0,
            "method": "error",
            "note": f"RAGAS评估异常: {str(e)}"
        }
