package com.payment.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 控制台清算概览 DTO，供管理页面展示整体清算情况。
 */
public class ConsoleOverviewDTO {

    public LocalDateTime generatedAt;
    public Summary summary;
    public List<TradeBillItem> recentBills;
    public List<ClearanceTaskItem> recentTasks;
    public List<AccountItem> accounts;
    public List<SettlementOrderItem> recentSettleOrders;
    public List<AccountFlowItem> recentFlows;
    public List<ReconcileBillItem> recentReconcileBills;

    public static class Summary {
        public long pendingBills;
        public long clearingBills;
        public long clearedBills;
        public long failedBills;
        public long waitOriginBills;
        public long pendingTasks;
        public long runningTasks;
        public long successTasks;
        public long failedTasks;
        public long deadTasks;
        public BigDecimal totalWaitBalance;
        public BigDecimal totalFrozenBalance;
        public long createdOrders;
        public long frozenOrders;
        public long payingOrders;
        public long successOrders;
        public long failedOrders;
        public long disputeOrders;
    }

    public static class TradeBillItem {
        public String billNo;
        public Long merchantId;
        public Integer billType;
        public BigDecimal tradeAmount;
        public Integer status;
        public String statusName;
        public String businessLine;
        public LocalDateTime createTime;
    }

    public static class ClearanceTaskItem {
        public String billNo;
        public Long merchantId;
        public Integer status;
        public String statusName;
        public Integer retryCount;
        public String errorMsg;
        public LocalDateTime nextRetryTime;
        public LocalDateTime updateTime;
    }

    public static class AccountItem {
        public Long merchantId;
        public BigDecimal waitBalance;
        public BigDecimal frozenBalance;
        public BigDecimal availableBalance;
        public Integer settleMode;
        public String settleCardNo;
    }

    public static class SettlementOrderItem {
        public String settleNo;
        public Long merchantId;
        public BigDecimal settleAmount;
        public Integer status;
        public String statusName;
        public String channelTradeNo;
        public String failReason;
        public LocalDateTime updateTime;
    }

    public static class AccountFlowItem {
        public Long merchantId;
        public String billNo;
        public String settleNo;
        public Integer opType;
        public BigDecimal amount;
        public BigDecimal beforeBalance;
        public BigDecimal afterBalance;
        public LocalDateTime createTime;
    }

    public static class ReconcileBillItem {
        public Long merchantId;
        public LocalDate billDate;
        public BigDecimal totalIncome;
        public BigDecimal totalSettle;
        public String fileUrl;
    }
}
