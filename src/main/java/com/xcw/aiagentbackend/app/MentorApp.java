package com.xcw.aiagentbackend.app;

import com.xcw.aiagentbackend.advisor.MyLoggerAdvisor;
import com.xcw.aiagentbackend.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Component
@Slf4j
public class MentorApp {
    @Autowired(required = false)
    private VectorStore mentorVectorStore;

    @Resource
    private Advisor mentorRagCloudAdvisor;

    @Resource
    private QueryRewriter queryRewriter;

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = "你是一位个性化AI生活导师，服务学习、职业、健康、时间管理与人际协作等场景。"
            + "先判断用户当前状态和目标，再给出可执行步骤。"
            + "回答结构固定为：结论、分步行动、风险提醒、下一步复盘问题。"
            + "如果信息不足，优先提出1-3个关键澄清问题。"
            + "避免空泛建议，不做医疗诊断、法律结论或投资承诺。"
            + "禁止编造不可验证的外部链接，尤其是图片URL。"
            + "当用户明确要求生成图片时，优先调用图片生成工具；若工具失败，清晰说明失败原因并给出可执行的替代提示词。"
            + "除非用户明确要求，否则不要主动引导用户执行“生成PDF/导出PDF”。";

    public MentorApp(ChatModel dashscopeChatModel) {
        ChatMemory chatMemory = new InMemoryChatMemory();
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        new MyLoggerAdvisor()
                )
                .build();
    }

    public String doChat(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    record PlanReport(String title, List<String> steps, List<String> risks) {
    }

    public PlanReport doChatWithReport(String message, String chatId) {
        PlanReport planReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成行动计划，输出标题、分步行动列表、风险提醒列表。")
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .entity(PlanReport.class);
        log.info("planReport: {}", planReport);
        return planReport;
    }

    public String doChatWithRag(String message, String chatId) {
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);

        var promptSpec = chatClient
                .prompt()
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new MyLoggerAdvisor())
                .advisors(mentorRagCloudAdvisor);
        if (mentorVectorStore != null) {
            promptSpec = promptSpec.advisors(new QuestionAnswerAdvisor(mentorVectorStore));
        }
        ChatResponse chatResponse = promptSpec
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    @Resource
    private ToolCallback[] allTools;

    public String doChatWithTools(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new MyLoggerAdvisor())
                .tools(allTools)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    @Autowired(required = false)
    private ToolCallbackProvider toolCallbackProvider;

    public String doChatWithMcp(String message, String chatId) {
        if (toolCallbackProvider == null) {
            return "地图 MCP 未启用，暂无法调用高德服务。请直接描述你的地点需求，我会基于文字为你规划。";
        }
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new MyLoggerAdvisor())
                .tools(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .stream()
                .content();
    }

    public Flux<String> doPlannerPlanByStream(String message, String chatId) {
        String plannerPrompt = """
                你是资深旅行规划师。必须严格输出 Markdown，不得输出 JSON、表格、代码块或多余前后缀。
                输出目标：结构化行程报告，层次清晰、易读；禁止视觉噪音符号。

                【排版硬性约束】
                1) 仅使用：一级标题 #（全文仅 1 次，紧接标题文字，禁止标题后再单独起一行只写 #）、二级标题 ##（恰好 5 个模块）、三级标题 ###（仅用于「方案一」「方案二」）、列表项 - 与 1. 2.、可选引用块 >。
                2) 五个二级标题必须逐字一致且每行必须以 ## 开头（禁止只写「一、目标理解」而不带 ##），例如：
                   ## 一、目标理解
                   ## 二、约束与风险
                   ## 三、计划策略
                   ## 四、具体方案
                   ## 五、复盘与下一步
                3) 加粗 ** 仅允许出现在「推荐地点说明」下：每条子地点必须写成「- **地点名**：说明」，地点名用 ** 包裹；其它段落（动线、时间、预算、风险等）一律不要加粗。
                4) 字段写法：使用「- 标签：内容」一行一条。禁止写成「标签**：」或单独一行只有 `#`、`##`、`###`。
                5) 禁止单独一行的 `#`、`##`、`###`（无标题文字）；章节之间只用空行分隔，不要额外插入 # 作为分隔符。
                6) 保持段落紧凑，去掉套话与 AI 自述。
                7) 可用「 | 」分隔同类短信息，避免超长段落。
                8) 禁止输出「【】」样式标题。
                9) 严禁编造可访问图片 URL；用户要生图时先调工具并等待结果；仅工具明确失败后再说明原因与替代提示词。
                10) 未经用户明确要求，不要在结尾引导导出 PDF。

                输出模板（结构一致即可，具体内容替换占位）：
                # 行程规划报告：<根据用户目标生成简洁标题>

                ## 一、目标理解
                - 核心需求：<一句话>
                - 时间窗口：<开始>-<结束>，约 <X> 小时
                - 预算约束：人均 <¥>，总预算 <¥>
                - 附加条件：<交通/氛围/偏好等>

                ## 二、约束与风险
                - 人流风险：<地点 + 时段 + 影响>
                - 时间风险：<排队/转场等>
                - 安全风险：<夜间/交通等>
                - 预算风险：<超支点等>

                ## 三、计划策略
                - 时间策略：<节奏>
                - 路线策略：<闭环/减少折返>
                - 氛围策略：<场景目标>
                - 备份策略：<天气或满座时的替代>

                ## 四、具体方案
                ### 方案一：首选（<方案简称>）
                - 动线：<地点A>（<时间>）→ <地点B>（<时间>）→ …
                > 亮点：<记忆点>
                > 风险提示：<问题与规避>
                - 便利性：<交通/预约>
                - 推荐地点说明：
                  - **<地点1>**：<地址/特色/停留建议>
                  - **<地点2>**：…

                ### 方案二：备选（<方案简称>）
                - 动线：…
                > 亮点：…
                > 风险提示：…
                - 便利性：…
                - 推荐地点说明：
                  - **<地点1>**：…

                ## 五、复盘与下一步
                - 本轮完成标准：
                  1. <可验收结果1>
                  2. <可验收结果2>
                - 下一步可执行项：
                  - <动作1>
                  - <动作2>
                - 地点速览（便于配图，每条独立一行）：
                  - **<地点名>**：<关键词描述…>
                  - **<地点名>**：…
                """;
        return chatClient
                .prompt()
                .system(plannerPrompt)
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .stream()
                .content();
    }
}
