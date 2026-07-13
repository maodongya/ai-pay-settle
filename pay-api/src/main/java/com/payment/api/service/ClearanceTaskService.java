package com.payment.api.service; // API 服务接口所在包

/**
 * 清分任务服务接口，管理账单清分异步任务
 */
public interface ClearanceTaskService {
    /**
     * 创建清分任务
     *
     * @param billNo     账单号
     * @param merchantId 商户 ID
     */
    void createTask(String billNo, Long merchantId);

    /**
     * 执行指定账单的清分任务
     *
     * @param billNo 账单号
     */
    void executeTask(String billNo);

    /**
     * 执行清分任务（携带 merchantId 精准路由，MQ 消费推荐）
     */
    void executeTask(String billNo, Long merchantId);

    /**
     * 重试失败的清分任务
     *
     * @param limit 最大重试数量
     */
    void retryFailedTasks(int limit);
}
