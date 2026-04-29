package com.xcw.aiagentbackend.controller;

import com.xcw.aiagentbackend.common.BaseResponse;
import com.xcw.aiagentbackend.common.ResultUtils;
import com.xcw.aiagentbackend.model.chat.ExportRecord;
import com.xcw.aiagentbackend.service.ChatSessionService;
import com.xcw.aiagentbackend.service.PlanExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.Map;

@RestController
@RequestMapping("/chat")
public class PlanExportController {

    @Autowired
    private PlanExportService planExportService;

    @Autowired
    private ChatSessionService chatSessionService;

    @PostMapping("/sessions/{sessionId}/export-plan")
    public BaseResponse<Map<String, Object>> exportLatestPlan(@PathVariable String sessionId, Authentication authentication) {
        String username = String.valueOf(authentication.getPrincipal());
        ExportRecord record = planExportService.exportLatestPlan(username, sessionId);
        return ResultUtils.success(Map.of(
                "exportId", record.getId(),
                "downloadUrl", "/api/chat/exports/" + record.getId() + "/download"
        ));
    }

    @GetMapping("/exports/{exportId}/download")
    public ResponseEntity<Resource> downloadExport(@PathVariable Long exportId, Authentication authentication) {
        String username = String.valueOf(authentication.getPrincipal());
        ExportRecord record = chatSessionService.getExportRecord(username, exportId);
        File file = new File(record.getFilePath());
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }
}
