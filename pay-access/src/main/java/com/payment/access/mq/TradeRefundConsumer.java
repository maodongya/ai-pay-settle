package com.payment.access.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.api.dto.TradeBillDTO;
import com.payment.api.service.BillAccessService;
import com.payment.common.enums.BillType;
import com.payment.mq.MqMessageHandler;
import com.payment.mq.MqTopics;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class TradeRefundConsumer implements MqMessageHandler {

    private final BillAccessService billAccessService;
    private final ObjectMapper objectMapper;

    public TradeRefundConsumer(@Lazy BillAccessService billAccessService, ObjectMapper objectMapper) {
        this.billAccessService = billAccessService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String topic() {
        return MqTopics.TRADE_REFUND;
    }

    @Override
    public void handle(String payload) {
        try {
            TradeBillDTO bill = objectMapper.readValue(payload, TradeBillDTO.class);
            bill.billType = BillType.REFUND.getCode();
            billAccessService.submitBill(bill);
        } catch (Exception e) {
            throw new IllegalStateException("trade refund consume failed", e);
        }
    }
}
