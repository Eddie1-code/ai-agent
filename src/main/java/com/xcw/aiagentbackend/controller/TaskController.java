package com.xcw.aiagentbackend.controller;

import com.xcw.aiagentbackend.common.BaseResponse;
import com.xcw.aiagentbackend.common.ResultUtils;
import com.xcw.aiagentbackend.model.task.AsyncTaskRecord;
import com.xcw.aiagentbackend.model.task.TaskSubmitRequest;
import com.xcw.aiagentbackend.service.AsyncTaskService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/task")
public class TaskController {

    @Resource
    private AsyncTaskService asyncTaskService;

    @PostMapping("/submit")
    public BaseResponse<String> submit(@RequestBody TaskSubmitRequest request) {
        return ResultUtils.success(asyncTaskService.submit(request));
    }

    @GetMapping("/{taskId}")
    public BaseResponse<AsyncTaskRecord> query(@PathVariable String taskId) {
        return asyncTaskService.findByTaskId(taskId)
                .map(ResultUtils::success)
                .orElseGet(() -> new BaseResponse<>(404, null, "任务不存在"));
    }
}
