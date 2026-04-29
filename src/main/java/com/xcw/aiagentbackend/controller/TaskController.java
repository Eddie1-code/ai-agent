package com.xcw.aiagentbackend.controller;

import com.xcw.aiagentbackend.common.BaseResponse;
import com.xcw.aiagentbackend.common.ResultUtils;
import com.xcw.aiagentbackend.exception.BusinessException;
import com.xcw.aiagentbackend.exception.ErrorCode;
import com.xcw.aiagentbackend.model.task.AsyncTaskRecord;
import com.xcw.aiagentbackend.model.task.TaskSubmitRequest;
import com.xcw.aiagentbackend.service.AsyncTaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.annotation.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/task")
public class TaskController {

    @Resource
    private AsyncTaskService asyncTaskService;

    @PostMapping("/submit")
    public BaseResponse<String> submit(@RequestBody TaskSubmitRequest request, Authentication authentication,
                                       HttpServletRequest httpServletRequest) {
        String ownerType = "user";
        String ownerId = authentication == null ? "" : String.valueOf(authentication.getPrincipal());
        if (httpServletRequest.getAttribute("apiKeyId") != null) {
            ownerType = "apikey";
            ownerId = String.valueOf(httpServletRequest.getAttribute("apiKeyId"));
        }
        return ResultUtils.success(asyncTaskService.submit(request, ownerType, ownerId));
    }

    @GetMapping("/{taskId}")
    public BaseResponse<AsyncTaskRecord> query(@PathVariable String taskId) {
        return asyncTaskService.findByTaskId(taskId)
                .map(ResultUtils::success)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "任务不存在"));
    }

    @GetMapping("/latest")
    public BaseResponse<List<AsyncTaskRecord>> latest(@RequestParam(defaultValue = "20") int limit) {
        return ResultUtils.success(asyncTaskService.listLatest(Math.min(Math.max(limit, 1), 100)));
    }

    @PostMapping("/{taskId}/cancel")
    public BaseResponse<Boolean> cancel(@PathVariable String taskId, @RequestParam(defaultValue = "用户主动取消") String reason) {
        return ResultUtils.success(asyncTaskService.cancelTask(taskId, reason));
    }
}
