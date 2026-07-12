package com.payment.access.controller; // 运营台 API 包

import com.payment.access.service.DlqReplayService; // DLQ 重放服务
import com.payment.common.model.ApiResponse; // 统一响应
import org.springframework.web.bind.annotation.PostMapping; // POST
import org.springframework.web.bind.annotation.RequestBody; // Body
import org.springframework.web.bind.annotation.RequestMapping; // 路径
import org.springframework.web.bind.annotation.RestController; // REST

/**
 * DLQ 运营台 API：人工审核后重放死信消息。
 */
@RestController // REST 控制器
@RequestMapping("/api/v1/console/dlq") // 路径前缀
public class DlqController {

    private final DlqReplayService dlqReplayService; // 重放服务

    /** 构造注入 */
    public DlqController(DlqReplayService dlqReplayService) {
        this.dlqReplayService = dlqReplayService; // 保存服务
    }

    /**
     * 人工重放 DLQ 消息到原 Topic。
     */
    @PostMapping("/replay") // POST /api/v1/console/dlq/replay
    public ApiResponse<Void> replay(@RequestBody DlqReplayRequest request) {
        dlqReplayService.replay(request.targetTopic, request.tag, request.hashKey, // 调用重放
                request.payload, request.operatorId, request.remark); // 审计参数
        return ApiResponse.ok(null); // 成功
    }

    /** 重放请求体 */
    public static class DlqReplayRequest {
        /** 目标 Topic（原 Topic，非 DLQ 名） */
        public String targetTopic; // 必填
        /** 消息 Tag */
        public String tag; // 可选
        /** 有序发送 hashKey（settle/clearance 等） */
        public String hashKey; // 可选
        /** 消息 JSON 体 */
        public String payload; // 必填
        /** 操作人 ID */
        public Long operatorId; // 审计
        /** 备注 */
        public String remark; // 审计
    }
}
