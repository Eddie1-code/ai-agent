"""
Python MCP Server - 知域Agent 数据分析与文本处理工具

提供两个工具：
1. analyze_data - 对输入数据做基础统计分析（均值/中位数/标准差/分布）
2. summarize_text - 对长文本做基础摘要（关键词提取、段落统计）

通过stdio协议被Java主应用调用，展示跨语言MCP集成能力。
"""

from mcp.server.fastmcp import FastMCP

mcp = FastMCP("Python Tools MCP Server")


@mcp.tool()
def analyze_data(data_json: str) -> str:
    """
    对输入的JSON数组数据做基础统计分析。
    输入格式: "[1, 2, 3, 4, 5]" 或 '[{"age":25}, {"age":30}, ...]'

    返回：数据量、均值、中位数、标准差、最小值、最大值。
    """
    import json

    try:
        data = json.loads(data_json)
    except json.JSONDecodeError:
        return "错误：无法解析输入数据。请提供有效的JSON数组。"

    if not isinstance(data, list) or len(data) == 0:
        return "错误：输入必须是包含至少1个元素的JSON数组。"

    # 处理对象数组：提取第一个数值字段
    if isinstance(data[0], dict):
        # 找到第一个数值字段
        numeric_keys = []
        for sample in data[:5]:
            for k, v in sample.items():
                if isinstance(v, (int, float)):
                    numeric_keys.append(k)

        if not numeric_keys:
            return "错误：未找到数值字段。请确保数据中包含数字。"

        # 用最常见的数值字段
        key = max(set(numeric_keys), key=numeric_keys.count)
        values = [item.get(key, 0) for item in data]
        field_info = f" (字段: {key})"
    else:
        values = data
        field_info = ""

    import statistics

    total = len(values)
    mean_val = statistics.mean(values)
    median_val = statistics.median(values)
    stdev_val = statistics.stdev(values) if total > 1 else 0
    min_val = min(values)
    max_val = max(values)

    return (
        f"统计分析结果{field_info}：\n"
        f"  数据量: {total}\n"
        f"  均值: {mean_val:.2f}\n"
        f"  中位数: {median_val:.2f}\n"
        f"  标准差: {stdev_val:.2f}\n"
        f"  最小值: {min_val:.2f}\n"
        f"  最大值: {max_val:.2f}\n"
        f"  范围: {max_val - min_val:.2f}"
    )


@mcp.tool()
def summarize_text(text: str) -> str:
    """
    对输入的长文本做基础统计和关键词提取。
    输入：任意文本字符串。

    返回：字符数、段落数、句子数、高频关键词。
    """
    if not text or not text.strip():
        return "错误：输入文本为空。"

    text = text.strip()

    # 基础统计
    char_count = len(text)
    paragraphs = [p.strip() for p in text.split("\n") if p.strip()]
    para_count = len(paragraphs)

    # 句子统计（中英文句末标点）
    import re
    sentences = re.split(r'[。！？.!?\n]+', text)
    sentences = [s.strip() for s in sentences if s.strip()]
    sent_count = len(sentences)

    # 中文分词尝试
    words = []
    try:
        # 简单的中文提取：连续汉字或连续英文单词
        chinese_words = re.findall(r'[一-鿿]{2,}', text)
        english_words = re.findall(r'[a-zA-Z]{2,}', text.lower())
        words = chinese_words + english_words
    except Exception:
        words = text.split()

    # 词频统计
    from collections import Counter
    word_freq = Counter(words).most_common(10)
    top_words = ", ".join([f"{w}({c})" for w, c in word_freq])

    return (
        f"文本分析结果：\n"
        f"  总字符数: {char_count}\n"
        f"  段落数: {para_count}\n"
        f"  句子数: ~{sent_count}\n"
        f"  词汇量: ~{len(set(words))} (去重)\n"
        f"  高频词: {top_words if top_words else '无明显高频词'}\n"
        f"  平均句长: ~{char_count // max(sent_count, 1)} 字符/句"
    )


def main():
    """入口：通过stdio运行MCP Server"""
    import sys
    print("[Python MCP Server] 启动中...", file=sys.stderr)
    mcp.run(transport="stdio")


if __name__ == "__main__":
    main()
