package com.payment.access.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.api.dto.ConsoleOverviewDTO;
import com.payment.api.service.ConsoleDashboardService;
import com.payment.common.enums.BillStatus;
import com.payment.common.enums.SettleOrderStatus;
import com.payment.common.enums.TaskStatus;
import com.payment.domain.entity.AccountFlowEntity;
import com.payment.domain.entity.ClearanceTaskEntity;
import com.payment.domain.entity.MerchantSettleAccountEntity;
import com.payment.domain.entity.ReconcileBillEntity;
import com.payment.domain.entity.SettlementOrderEntity;
import com.payment.domain.entity.TradeBillEntity;
import com.payment.domain.mapper.AccountFlowMapper;
import com.payment.domain.mapper.ClearanceTaskMapper;
import com.payment.domain.mapper.ReconcileBillMapper;
import com.payment.domain.mapper.SettlementOrderMapper;
import com.payment.domain.mapper.TradeBillMapper;
import com.payment.domain.repository.ClearanceTaskRepository;
import com.payment.domain.repository.MerchantSettleAccountRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 控制台清算概览服务实现。
 */
@Service
public class ConsoleDashboardServiceImpl implements ConsoleDashboardService {

    private static final int RECENT_LIMIT = 20;

    private final ClearanceTaskRepository clearanceTaskRepository;
    private final ClearanceTaskMapper clearanceTaskMapper;
    private final TradeBillMapper tradeBillMapper;
    private final MerchantSettleAccountRepository accountRepository;
    private final SettlementOrderMapper settlementOrderMapper;
    private final AccountFlowMapper accountFlowMapper;
    private final ReconcileBillMapper reconcileBillMapper;

    public ConsoleDashboardServiceImpl(ClearanceTaskRepository clearanceTaskRepository,
                                       ClearanceTaskMapper clearanceTaskMapper,
                                       TradeBillMapper tradeBillMapper,
                                       MerchantSettleAccountRepository accountRepository,
                                       SettlementOrderMapper settlementOrderMapper,
                                       AccountFlowMapper accountFlowMapper,
                                       ReconcileBillMapper reconcileBillMapper) {
        this.clearanceTaskRepository = clearanceTaskRepository;
        this.clearanceTaskMapper = clearanceTaskMapper;
        this.tradeBillMapper = tradeBillMapper;
        this.accountRepository = accountRepository;
        this.settlementOrderMapper = settlementOrderMapper;
        this.accountFlowMapper = accountFlowMapper;
        this.reconcileBillMapper = reconcileBillMapper;
    }

    @Override
    public ConsoleOverviewDTO getOverview() {
        ConsoleOverviewDTO dto = new ConsoleOverviewDTO();
        dto.generatedAt = LocalDateTime.now();
        dto.summary = buildSummary();
        dto.recentBills = recentBills();
        dto.recentTasks = recentTasks();
        dto.accounts = listAccounts();
        dto.recentSettleOrders = recentSettleOrders();
        dto.recentFlows = recentFlows();
        dto.recentReconcileBills = recentReconcileBills();
        return dto;
    }

    private ConsoleOverviewDTO.Summary buildSummary() {
        ConsoleOverviewDTO.Summary summary = new ConsoleOverviewDTO.Summary();
        summary.pendingBills = countBillStatus(BillStatus.PENDING);
        summary.clearingBills = countBillStatus(BillStatus.CLEARING);
        summary.clearedBills = countBillStatus(BillStatus.CLEARED);
        summary.failedBills = countBillStatus(BillStatus.FAILED);
        summary.waitOriginBills = countBillStatus(BillStatus.WAIT_ORIGIN);

        summary.pendingTasks = clearanceTaskRepository.countByStatus(TaskStatus.PENDING.getCode());
        summary.runningTasks = clearanceTaskRepository.countByStatus(TaskStatus.RUNNING.getCode());
        summary.successTasks = clearanceTaskRepository.countByStatus(TaskStatus.SUCCESS.getCode());
        summary.failedTasks = clearanceTaskRepository.countByStatus(TaskStatus.FAILED.getCode());
        summary.deadTasks = clearanceTaskRepository.countByStatus(TaskStatus.DEAD.getCode());

        BigDecimal totalWait = BigDecimal.ZERO;
        BigDecimal totalFrozen = BigDecimal.ZERO;
        for (MerchantSettleAccountEntity account : accountRepository.findAll()) {
            totalWait = totalWait.add(nullToZero(account.waitBalance));
            totalFrozen = totalFrozen.add(nullToZero(account.frozenBalance));
        }
        summary.totalWaitBalance = totalWait;
        summary.totalFrozenBalance = totalFrozen;

        summary.createdOrders = countSettleStatus(SettleOrderStatus.CREATED);
        summary.frozenOrders = countSettleStatus(SettleOrderStatus.FROZEN);
        summary.payingOrders = countSettleStatus(SettleOrderStatus.PAYING);
        summary.successOrders = countSettleStatus(SettleOrderStatus.SUCCESS);
        summary.failedOrders = countSettleStatus(SettleOrderStatus.FAILED);
        summary.disputeOrders = countSettleStatus(SettleOrderStatus.DISPUTE);
        return summary;
    }

    private List<ConsoleOverviewDTO.TradeBillItem> recentBills() {
        return tradeBillMapper.selectList(new QueryWrapper<TradeBillEntity>()
                        .orderByDesc("create_time")
                        .last("LIMIT " + RECENT_LIMIT))
                .stream()
                .map(this::toBillItem)
                .collect(Collectors.toList());
    }

    private List<ConsoleOverviewDTO.ClearanceTaskItem> recentTasks() {
        return clearanceTaskMapper.selectList(new QueryWrapper<ClearanceTaskEntity>()
                        .orderByDesc("update_time")
                        .last("LIMIT " + RECENT_LIMIT))
                .stream()
                .map(this::toTaskItem)
                .collect(Collectors.toList());
    }

    private List<ConsoleOverviewDTO.AccountItem> listAccounts() {
        return accountRepository.findAll().stream()
                .map(this::toAccountItem)
                .collect(Collectors.toList());
    }

    private List<ConsoleOverviewDTO.SettlementOrderItem> recentSettleOrders() {
        return settlementOrderMapper.selectList(new QueryWrapper<SettlementOrderEntity>()
                        .orderByDesc("update_time")
                        .last("LIMIT " + RECENT_LIMIT))
                .stream()
                .map(this::toSettleOrderItem)
                .collect(Collectors.toList());
    }

    private List<ConsoleOverviewDTO.AccountFlowItem> recentFlows() {
        return accountFlowMapper.selectList(new QueryWrapper<AccountFlowEntity>()
                        .orderByDesc("create_time")
                        .last("LIMIT " + RECENT_LIMIT))
                .stream()
                .map(this::toFlowItem)
                .collect(Collectors.toList());
    }

    private List<ConsoleOverviewDTO.ReconcileBillItem> recentReconcileBills() {
        return reconcileBillMapper.selectList(new QueryWrapper<ReconcileBillEntity>()
                        .orderByDesc("bill_date")
                        .last("LIMIT " + RECENT_LIMIT))
                .stream()
                .map(this::toReconcileItem)
                .collect(Collectors.toList());
    }

    private long countBillStatus(BillStatus status) {
        return tradeBillMapper.selectCount(new QueryWrapper<TradeBillEntity>()
                .eq("status", status.getCode()));
    }

    private long countSettleStatus(SettleOrderStatus status) {
        return settlementOrderMapper.selectCount(new QueryWrapper<SettlementOrderEntity>()
                .eq("status", status.getCode()));
    }

    private ConsoleOverviewDTO.TradeBillItem toBillItem(TradeBillEntity entity) {
        ConsoleOverviewDTO.TradeBillItem item = new ConsoleOverviewDTO.TradeBillItem();
        item.billNo = entity.billNo;
        item.merchantId = entity.merchantId;
        item.billType = entity.billType;
        item.tradeAmount = entity.tradeAmount;
        item.status = entity.status;
        item.statusName = billStatusName(entity.status);
        item.businessLine = entity.businessLine;
        item.createTime = entity.createTime;
        return item;
    }

    private ConsoleOverviewDTO.ClearanceTaskItem toTaskItem(ClearanceTaskEntity entity) {
        ConsoleOverviewDTO.ClearanceTaskItem item = new ConsoleOverviewDTO.ClearanceTaskItem();
        item.billNo = entity.billNo;
        item.merchantId = entity.merchantId;
        item.status = entity.status;
        item.statusName = taskStatusName(entity.status);
        item.retryCount = entity.retryCount;
        item.errorMsg = entity.errorMsg;
        item.nextRetryTime = entity.nextRetryTime;
        item.updateTime = entity.updateTime;
        return item;
    }

    private ConsoleOverviewDTO.AccountItem toAccountItem(MerchantSettleAccountEntity entity) {
        ConsoleOverviewDTO.AccountItem item = new ConsoleOverviewDTO.AccountItem();
        item.merchantId = entity.merchantId;
        item.waitBalance = entity.waitBalance;
        item.frozenBalance = entity.frozenBalance;
        item.availableBalance = nullToZero(entity.waitBalance).subtract(nullToZero(entity.frozenBalance));
        item.settleMode = entity.settleMode;
        item.settleCardNo = entity.settleCardNo;
        return item;
    }

    private ConsoleOverviewDTO.SettlementOrderItem toSettleOrderItem(SettlementOrderEntity entity) {
        ConsoleOverviewDTO.SettlementOrderItem item = new ConsoleOverviewDTO.SettlementOrderItem();
        item.settleNo = entity.settleNo;
        item.merchantId = entity.merchantId;
        item.settleAmount = entity.settleAmount;
        item.status = entity.status;
        item.statusName = settleStatusName(entity.status);
        item.channelTradeNo = entity.channelTradeNo;
        item.failReason = entity.failReason;
        item.updateTime = entity.updateTime;
        return item;
    }

    private ConsoleOverviewDTO.AccountFlowItem toFlowItem(AccountFlowEntity entity) {
        ConsoleOverviewDTO.AccountFlowItem item = new ConsoleOverviewDTO.AccountFlowItem();
        item.merchantId = entity.merchantId;
        item.billNo = entity.billNo;
        item.settleNo = entity.settleNo;
        item.opType = entity.opType;
        item.amount = entity.amount;
        item.beforeBalance = entity.beforeBalance;
        item.afterBalance = entity.afterBalance;
        item.createTime = entity.createTime;
        return item;
    }

    private ConsoleOverviewDTO.ReconcileBillItem toReconcileItem(ReconcileBillEntity entity) {
        ConsoleOverviewDTO.ReconcileBillItem item = new ConsoleOverviewDTO.ReconcileBillItem();
        item.merchantId = entity.merchantId;
        item.billDate = entity.billDate;
        item.totalIncome = entity.totalIncome;
        item.totalSettle = entity.totalSettle;
        item.fileUrl = entity.fileUrl;
        return item;
    }

    private String billStatusName(Integer status) {
        if (status == null) {
            return "-";
        }
        try {
            return switch (BillStatus.of(status)) {
                case PENDING -> "待处理";
                case CLEARING -> "清分中";
                case CLEARED -> "已清分";
                case FAILED -> "清分失败";
                case WAIT_ORIGIN -> "等待原单";
            };
        } catch (IllegalArgumentException ex) {
            return String.valueOf(status);
        }
    }

    private String taskStatusName(Integer status) {
        if (status == null) {
            return "-";
        }
        try {
            return switch (TaskStatus.of(status)) {
                case PENDING -> "待执行";
                case RUNNING -> "执行中";
                case SUCCESS -> "成功";
                case FAILED -> "失败";
                case DEAD -> "死信";
            };
        } catch (IllegalArgumentException ex) {
            return String.valueOf(status);
        }
    }

    private String settleStatusName(Integer status) {
        if (status == null) {
            return "-";
        }
        try {
            return switch (SettleOrderStatus.of(status)) {
                case CREATED -> "已创建";
                case FROZEN -> "已冻结";
                case PAYING -> "打款中";
                case SUCCESS -> "打款成功";
                case FAILED -> "打款失败";
                case DISPUTE -> "争议中";
            };
        } catch (IllegalArgumentException ex) {
            return String.valueOf(status);
        }
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
