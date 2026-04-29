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

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    public String doChatWithMcp(String message, String chatId) {
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
                你是AI规划师。你必须严格输出 Markdown，不得输出 JSON、表格或多余前后缀。
                强制要求：
                1) 仅使用 Markdown 标题和列表语法：#、##、###、-、1.
                2) 每个要点单独一行，自动换行友好，不得把多个要点写在同一行。
                3) 必须包含以下五个二级标题（顺序固定，标题文案必须完全一致）：
                   ## 一、目标理解
                   ## 二、约束与风险
                   ## 三、计划策略
                   ## 四、具体方案
                   ## 五、复盘与下一步
                4) 在“具体方案”下，至少给出 2 个三级标题方案（### 方案一 / ### 方案二），每个方案含“动线、亮点、便利性”三个分点。
                5) 时间、地点、价格等关键信息用 **加粗**。
                6) 禁止输出“【】”样式标题，禁止把内容写成一整段长文本。
                7) 严禁编造或猜测可访问图片URL；当用户请求生图时，先调用图片工具并等待结果，不要在工具执行中提前输出失败文案；仅在工具明确失败后，再给出友好失败原因与替代提示词。
                8) 未经用户明确要求，不要主动在结尾引导“是否导出PDF/是否生成PDF”。

                输出模板（按此骨架组织）：
                # 行程规划报告：<根据用户目标自动生成标题>

                ## 一、目标理解
                - 核心需求：
                - 关键矛盾：
                - 具体规避：
                - 附加条件：

                ## 二、约束与风险
                - 人流风险：
                - 时间风险：
                - 安全风险：

                ## 三、计划策略
                - **时间策略**：
                - **安全策略**：
                - **氛围策略**：
                - **缓冲策略**：

                ## 四、具体方案
                ### 方案一：首选
                - **动线**：
                - **亮点**：
                - **便利性**：

                ### 方案二：备选
                - **动线**：
                - **亮点**：
                - **便利性**：

                ## 五、复盘与下一步
                - **本轮完成标准**：
                  1. 
                  2. 
                - **下一步可选项**：
                  - 
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
