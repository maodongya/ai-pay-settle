package com.payment.mq.support; // MQ 消费异常分类器包

import com.payment.common.exception.BizException; // 业务异常，携带错误码
import com.payment.common.exception.ErrorCode; // 统一错误码枚举
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException; // JSON 未知字段
import com.payment.mq.exception.NonRetryableException; // 不可 MQ 重试标记异常
import org.springframework.dao.CannotAcquireLockException; // 数据库锁等待异常
import org.springframework.dao.RecoverableDataAccessException; // 可恢复的数据访问异常
import org.springframework.dao.TransientDataAccessException; // 瞬态数据访问异常
import org.springframework.stereotype.Component; // 注册为 Spring 组件

import java.net.SocketTimeoutException; // 网络超时
import java.sql.SQLTransientException; // JDBC 瞬态异常

/**
 * 将消费过程中的异常映射为 RocketMQ 处置动作：重试 / 直接 ACK / 快速进 DLQ。
 */
@Component // 注入到各 Consumer Listener 代理层
public class MqConsumeExceptionClassifier {

    /** MQ 消费处置动作枚举 */
    public enum MqConsumeAction {
        /** 抛出异常，触发 RocketMQ Client L1 重试 */
        RETRY,
        /** 记录日志后直接 ACK，不再重试 */
        ACK,
        /** 记录错误后抛出，尽快进入 DLQ 等待人工 */
        DLQ
    }

    /**
     * 对异常进行分类，决定 Listener 的 ACK 策略。
     *
     * @param e 消费过程中捕获的异常（可能已包装）
     * @return 处置动作
     */
    public MqConsumeAction classify(Throwable e) {
        Throwable root = unwrap(e); // 解包到根因异常
        if (root instanceof NonRetryableException) { // 显式标记不可重试（如任务已 DEAD）
            return MqConsumeAction.ACK; // 业务侧已落库，直接 ACK 避免 RETRY 队列堆积
        }
        if (root instanceof UnrecognizedPropertyException) { // JSON 字段与 DTO 不匹配
            return MqConsumeAction.DLQ; // 脏消息不重试，避免 RETRY 队列堆积
        }
        if (root instanceof BizException be) { // 业务异常按错误码分流
            return classifyBiz(be); // 根据业务码决策
        }
        if (isTransient(root)) { // 瞬态基础设施故障
            return MqConsumeAction.RETRY; // 走 MQ 退避重试
        }
        return MqConsumeAction.DLQ; // 未知异常默认进 DLQ，避免无限重试
    }

    /**
     * 按 BizException 错误码映射处置动作。
     */
    private MqConsumeAction classifyBiz(BizException be) {
        int code = be.getCode(); // 读取业务错误码
        if (code == ErrorCode.DUPLICATE_BILL.getCode()) { // 10001 重复账单
            return MqConsumeAction.ACK; // 幂等成功，直接 ACK
        }
        if (code == ErrorCode.ORIGIN_BILL_NOT_FOUND.getCode()) { // 10004 等待原单
            return MqConsumeAction.ACK; // 业务等待，不应 MQ 重试
        }
        if (code == ErrorCode.INVALID_PARAM.getCode() // 10002 参数非法
                || code == ErrorCode.MERCHANT_INVALID.getCode() // 10003 商户冻结/不存在
                || code == ErrorCode.RULE_NOT_MATCHED.getCode()) { // 20001 规则缺失
            return MqConsumeAction.ACK; // 永久性数据问题，重试无意义
        }
        if (code == ErrorCode.CONCURRENT_UPDATE.getCode()) { // 30005 乐观锁冲突
            return MqConsumeAction.RETRY; // 瞬态并发，可 MQ 重试
        }
        return MqConsumeAction.RETRY; // 其他业务异常默认 MQ 重试
    }

    /**
     * 判断是否为瞬态可恢复异常（网络/连接/锁等待等）。
     */
    private boolean isTransient(Throwable e) {
        if (e instanceof TransientDataAccessException) { // Spring 瞬态 DAO
            return true; // 可重试
        }
        if (e instanceof RecoverableDataAccessException) { // 可恢复 DAO
            return true; // 可重试
        }
        if (e instanceof CannotAcquireLockException) { // 锁等待
            return true; // 可重试
        }
        if (e instanceof SQLTransientException) { // JDBC 瞬态
            return true; // 可重试
        }
        if (e instanceof SocketTimeoutException) { // 网络超时
            return true; // 可重试
        }
        Throwable cause = e.getCause(); // 继续向下查找
        return cause != null && cause != e && isTransient(cause); // 递归判断根因
    }

    /**
     * 解包 IllegalStateException 等包装异常，找到根因。
     */
    private Throwable unwrap(Throwable e) {
        Throwable current = e; // 从当前异常开始
        while (current.getCause() != null && current.getCause() != current) { // 存在包装层
            if (current instanceof BizException || current instanceof NonRetryableException) { // 已是业务/标记异常
                break; // 停止解包
            }
            current = current.getCause(); // 向内一层
        }
        return current; // 返回根因或最内层业务异常
    }
}
