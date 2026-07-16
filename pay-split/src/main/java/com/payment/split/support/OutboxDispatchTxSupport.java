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

    public OutboxDispatchTxSupport(OutboxMessageRepository outboxMessageRepository) {
        this.outboxMessageRepository = outboxMessageRepository;
    }

    @Transactional
    public boolean markSent(Long id, Long merchantId) {
        return outboxMessageRepository.markSentByIdAndMerchantId(id, merchantId) > 0;
    }
}
