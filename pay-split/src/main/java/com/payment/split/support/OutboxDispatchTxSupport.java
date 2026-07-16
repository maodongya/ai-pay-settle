package com.payment.split.support;

import com.payment.domain.repository.OutboxMessageRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox 短事务：MQ 发送在事务外，仅标记 status=1 时持连。
 */
@Component
public class OutboxDispatchTxSupport {

    private final OutboxMessageRepository outboxMessageRepository;

    /**
     * 构造注入 Outbox 仓储。
     */
    public OutboxDispatchTxSupport(OutboxMessageRepository outboxMessageRepository) {
        this.outboxMessageRepository = outboxMessageRepository;
    }

    /**
     * 短事务标记 Outbox 已发送（status=1），返回是否更新成功。
     */
    @Transactional
    public boolean markSent(Long id, Long merchantId) {
        return outboxMessageRepository.markSentByIdAndMerchantId(id, merchantId) > 0;
    }
}
