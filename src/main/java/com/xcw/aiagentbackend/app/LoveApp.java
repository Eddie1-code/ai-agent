package com.xcw.aiagentbackend.app;


import com.xcw.aiagentbackend.advisor.MyLoggerAdvisor;
import com.xcw.aiagentbackend.advisor.ReReadingAdvisor;
import com.xcw.aiagentbackend.chatmemory.FileBasedChatMemory;
import com.xcw.aiagentbackend.rag.LoveAppRagCustomAdvisorFactory;
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
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;


@Component
@Slf4j
public class LoveApp {
    @Resource
    private VectorStore loveAppVectorStore;

    @Resource
    private Advisor loveAppRagCloudAdvisor;

    // 移除对pgVectorVectorStore的依赖
    // @Resource
    // private VectorStore pgVectorVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    private final ChatClient chatClient;

    // 系统提示，定义应用的角色和行为
    private static final String SYSTEM_PROMPT = "扮演深耕恋爱心理领域的专家。开场向用户表明身份，告知用户可倾诉恋爱难题。" +
            "围绕单身、恋爱、已婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰；" +
            "恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题。" +
            "引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。";

    /**
     * 初始化 ChatClient
     *
     * @param dashscopeChatModel 使用的聊天模型
     */
    public LoveApp(ChatModel dashscopeChatModel) {
        // 初始化基于文件的对话记忆
        String fileDir = System.getProperty("user.dir") + "/chat-memory";
        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
        // 初始化基于内存的对话记忆
        // ChatMemory chatMemory = new InMemoryChatMemory();
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        // 添加自定义日志 Advisor, 可按需开启
                        new MyLoggerAdvisor()
//                        // 添加自定义 Re2 Advisor, 可按需开启
//                        new ReReadingAdvisor()
                )
                .build();
    }

    /**
     * AI 基础对话（支持多轮对话记忆）   ==> 处理用户输入，生成回复
     *
     * @param message 用户输入
     * @param chatId  对话ID，用于区分不同用户的对话
     * @return 生成的回复
     */
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

    //Java14+ 语法
    //定义恋爱报告数据结构
    record LoveReport(String title, List<String> suggestions) {
    }

    /**
     * AI 对话并生成恋爱报告(实现结构化输出)  ==> 处理用户输入，生成回复和恋爱报告
     *
     * @param message 用户输入
     * @param chatId  对话ID，用于区分不同用户的对话
     * @return 生成的恋爱报告
     */
    public LoveReport doChatWithReport(String message, String chatId) {
        LoveReport loveReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成恋爱结果，标题为{用户名}的恋爱报告，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .entity(LoveReport.class);
        log.info("loveReport: {}", loveReport);
        return loveReport;
    }

    //AI 恋爱大师知识库问答功能

    /**
     * AI 对话并结合知识库问答  ==> 处理用户输入，结合知识库生成回复
     *
     * @param message 用户输入
     * @param chatId  对话ID，用于区分不同用户的对话
     * @return 生成的回复
     */
    public String doChatWithRag(String message, String chatId) {
        // 先进行查询重写
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);

        ChatResponse chatResponse = chatClient
                .prompt()
                // 使用重写后的查询进行对话
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                // 应用 RAG 检索增强服务（基于本地知识库服务）
                .advisors(new QuestionAnswerAdvisor(loveAppVectorStore)) // 也可以添加 QA Advisor
                // 应用 RAG 检索增强服务（基于云知识库服务）
                //.advisors(loveAppRagCloudAdvisor)
                // 应用 RAG 检索增强服务（基于 PgVector 向量存储）
                //.advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                //
//                .advisors(LoveAppRagCustomAdvisorFactory.createLoveAppRagCustomAdvisor(
//                        loveAppVectorStore, "已婚"))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    //AI调用
    @Resource
    private ToolCallback[] allTools; // 注入所有工具(列表)

    /**
     * AI 调用工具执行操作  ==> 处理用户输入，调用工具生成回复
     *
     * @param message 用户输入
     * @param chatId  对话ID，用于区分不同用户的对话
     * @return 生成的回复
     */
    public String doChatWithTools(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
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

    /**
     * AI 调用工具执行操作  ==> 处理用户输入，调用工具生成回复
     *
     * @param message 用户输入
     * @param chatId  对话ID，用于区分不同用户的对话
     * @return 生成的回复
     */
    public String doChatWithMcp(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .tools(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 基础对话流式响应（支持多轮对话记忆）  ==> 处理用户输入，生成流式回复
     *
     * @param message 用户输入
     * @param chatId  对话ID，用于区分不同用户的对话
     * @return 流式回复内容
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .stream()
                .content();
    }
}
