package com.xcw.aiagentbackend.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MentorDocumentLoaderTest {

    @Resource
    private MentorDocumentLoader mentorDocumentLoader;

    @Test
    void loadMarkdowns() {
        mentorDocumentLoader.loadMarkdowns();
    }
}
