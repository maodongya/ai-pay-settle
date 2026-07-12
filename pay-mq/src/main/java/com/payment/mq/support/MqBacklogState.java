package com.payment.mq.support; // MQ 积压运行时状态包

import org.springframework.stereotype.Component; // Spring 组件，跨模块共享熔断状态

import java.util.concurrent.atomic.AtomicBoolean; // 线程安全布尔开关

/**
 * 积压监控 Job 写入的运行时状态，供接入层熔断（HTTP 503）读取。
 */
@Component // 单例 Bean，BillAccessServiceImpl 可注入
public class MqBacklogState {

    /** 熔断是否打开：true 表示接入应拒绝新流量 */
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false); // 默认关闭熔断

    /** 最近一次巡检摘要，便于日志与排查 */
    private volatile String lastSummary = "ok"; // 初始为正常

    /** 打开熔断（积压严重） */
    public void openCircuit(String reason) {
        circuitOpen.set(true); // 置为打开
        lastSummary = reason; // 记录原因
    }

    /** 关闭熔断（积压恢复） */
    public void closeCircuit() {
        circuitOpen.set(false); // 置为关闭
        lastSummary = "ok"; // 恢复摘要
    }

    /** 熔断是否打开 */
    public boolean isCircuitOpen() {
        return circuitOpen.get(); // 读取当前状态
    }

    /** 获取最近巡检摘要 */
    public String getLastSummary() {
        return lastSummary; // 返回摘要文本
    }
}
