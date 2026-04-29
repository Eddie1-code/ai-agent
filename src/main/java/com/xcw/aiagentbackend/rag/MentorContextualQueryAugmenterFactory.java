package com.xcw.aiagentbackend.rag;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

public class MentorContextualQueryAugmenterFactory {
    public static ContextualQueryAugmenter createInstance() {
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate(
                "你应该输出下面的内容：\n"
                        + "抱歉，我只能回答AI生活导师相关的问题，其他内容暂时无法提供。\n"
                        + "如需帮助请联系维护者。"
        );
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }
}
