package com.xcw.aiagentbackend.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 基于文件持久化的对话记忆
 */
public class FileBasedChatMemory implements ChatMemory {

    private final String BASE_DIR;
    private static final ThreadLocal<Kryo> KRYO_THREAD_LOCAL = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
        return kryo;
    });

    // 构造对象时，指定文件保存目录
    public FileBasedChatMemory(String dir) {
        this.BASE_DIR = dir;
        File baseDir = new File(dir);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }

    // 添加单条消息
    @Override
    public void add(String conversationId, Message message) {
        saveConversation(conversationId, List.of(message));
    }

    // 添加多条消息
    @Override
    public void add(String conversationId, List<Message> messages) {
        List<Message> conversationMessages = getOrCreateConversation(conversationId);
        conversationMessages.addAll(messages);
        saveConversation(conversationId, conversationMessages);
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        List<Message> allMessages = getOrCreateConversation(conversationId);
        return allMessages.stream()
        .skip(Math.max(0, allMessages.size() - lastN))
        .toList();
    }

    @Override
    public void clear(String conversationId) {
        File file = getConversationFile(conversationId);
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * 获取或创建对话消息的列表
     * @param conversationId 对话ID
     * @return 对话消息列表
     */
    private List<Message> getOrCreateConversation(String conversationId) {
        File file = getConversationFile(conversationId);
        List<Message> messages = new ArrayList<>();
        if (file.exists()) {
            try (Input input = new Input(new FileInputStream(file))) {
                Kryo kryo = KRYO_THREAD_LOCAL.get();
                Object data = kryo.readClassAndObject(input);
                if (data instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Message message) {
                            messages.add(message);
                        }
                    }
                }
            } catch (Exception e) {
                // 历史 .kryo 格式或类ID不兼容时，隔离坏文件并回退空会话，避免对话流程中断
                quarantineBrokenFile(file);
                e.printStackTrace();
            }
        }
        return messages;
    }

    /**
     * 保存对话消息到文件
     * @param conversationId 对话ID
     * @param messages 对话消息列表
     */
    private void saveConversation(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        try (Output output = new Output(new FileOutputStream(file))) {
            Kryo kryo = KRYO_THREAD_LOCAL.get();
            kryo.writeClassAndObject(output, new ArrayList<>(messages));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void quarantineBrokenFile(File file) {
        if (!file.exists()) {
            return;
        }
        File broken = new File(file.getParentFile(), file.getName() + ".broken");
        if (broken.exists()) {
            Objects.requireNonNull(broken.delete());
        }
        if (!file.renameTo(broken)) {
            // rename 失败则尝试删除原文件，确保下一轮可正常写入
            Objects.requireNonNull(file.delete());
        }
    }

    /**
     * 获取对话文件(每个对话单独存一个文件)
     * @param conversationId 对话ID
     * @return 对应的文件
     */
    private File getConversationFile(String conversationId) {
        return new File(BASE_DIR, conversationId + ".kryo");
    }
}