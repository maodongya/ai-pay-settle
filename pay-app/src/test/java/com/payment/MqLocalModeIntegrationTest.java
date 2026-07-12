package com.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.api.dto.SettleAccountDTO;
import com.payment.api.dto.TradeBillDTO;
import com.payment.api.service.BillAccessService;
import com.payment.api.service.SettleAccountService;
import com.payment.common.enums.BillType;
import com.payment.mq.MqTopics;
import com.payment.mq.PayMqProducer;
import com.payment.split.job.OutboxDispatchJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MQ Local 模式集成测试：trade_pay / outbox→settle_amount 链路。
 */
@SpringBootTest
class MqLocalModeIntegrationTest {

    private static final BigDecimal EXPECTED_INCOME = new BigDecimal("905.89");

    @Autowired
    private PayMqProducer payMqProducer;

    @Autowired
    private BillAccessService billAccessService;

    @Autowired
    private SettleAccountService settleAccountService;

    @Autowired
    private OutboxDispatchJob outboxDispatchJob;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreditViaOutboxMqDispatch() {
        BigDecimal before = settleAccountService.queryBalance(100001L).waitBalance;
        billAccessService.submitBill(buildPayBill(newBillNo(), new BigDecimal("1000.00")));
        outboxDispatchJob.dispatch();

        SettleAccountDTO balance = settleAccountService.queryBalance(100001L);
        assertEquals(before.add(EXPECTED_INCOME), balance.waitBalance);
    }

    @Test
    void shouldSubmitBillViaTradePayTopic() throws Exception {
        BigDecimal before = settleAccountService.queryBalance(100001L).waitBalance;
        String billNo = newBillNo();
        TradeBillDTO bill = buildPayBill(billNo, new BigDecimal("1000.00"));

        payMqProducer.send(MqTopics.TRADE_PAY, objectMapper.writeValueAsString(bill));
        outboxDispatchJob.dispatch();

        SettleAccountDTO balance = settleAccountService.queryBalance(100001L);
        assertEquals(before.add(EXPECTED_INCOME), balance.waitBalance);
    }

    @Test
    void shouldNotDoubleCreditOnRepeatedOutboxDispatch() {
        BigDecimal before = settleAccountService.queryBalance(100001L).waitBalance;
        billAccessService.submitBill(buildPayBill(newBillNo(), new BigDecimal("1000.00")));

        outboxDispatchJob.dispatch();
        outboxDispatchJob.dispatch();

        SettleAccountDTO balance = settleAccountService.queryBalance(100001L);
        assertEquals(before.add(EXPECTED_INCOME), balance.waitBalance);
    }

    private String newBillNo() {
        return "CL" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private TradeBillDTO buildPayBill(String billNo, BigDecimal amount) {
        TradeBillDTO bill = new TradeBillDTO();
        bill.billNo = billNo;
        bill.billType = BillType.PAY.getCode();
        bill.businessLine = "A";
        bill.category = "A01";
        bill.serviceItem = "A0101";
        bill.merchantId = 100001L;
        bill.agentId = 200001L;
        bill.secondAgentId = 200002L;
        bill.orderNo = "OD" + billNo;
        bill.tradeAmount = amount;
        bill.cityCode = "110000";
        bill.payChannel = "ALI_PAY";
        return bill;
    }
}
