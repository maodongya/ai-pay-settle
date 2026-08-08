package com.payment.settlement.account;

import com.payment.common.enums.AccountFlowOpType;
import com.payment.common.exception.BizException;
import com.payment.common.exception.ErrorCode;
import com.payment.domain.entity.AccountFlowEntity;
import com.payment.domain.entity.MerchantSettleAccountEntity;
import com.payment.domain.repository.AccountFlowRepository;
import com.payment.domain.repository.MerchantSettleAccountRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户操作：靠 CAS + 流水幂等，不加 Redis 锁、不开独立事务（由调用方持短事务）。
 */
@Component
public class AccountOperator {

    private static final int MAX_RETRY = 3;

    private final MerchantSettleAccountRepository accountRepository;
    private final AccountFlowRepository accountFlowRepository;

    public AccountOperator(MerchantSettleAccountRepository accountRepository,
                           AccountFlowRepository accountFlowRepository) {
        this.accountRepository = accountRepository;
        this.accountFlowRepository = accountFlowRepository;
    }

    public MerchantSettleAccountEntity credit(Long merchantId, String billNo, BigDecimal amount, AccountFlowOpType opType) {
        if (accountFlowRepository.existsByBillNoAndOpType(billNo, opType.getCode())) {
            return accountRepository.findByMerchantId(merchantId).orElseThrow();
        }
        return updateBalance(merchantId, billNo, null, amount, opType, BigDecimal.ZERO);
    }

    public MerchantSettleAccountEntity freeze(Long merchantId, String settleNo, BigDecimal amount) {
        if (accountFlowRepository.existsBySettleNoAndOpType(settleNo, AccountFlowOpType.FREEZE.getCode())) {
            return accountRepository.findByMerchantId(merchantId).orElseThrow();
        }
        return updateBalance(merchantId, null, settleNo, amount.negate(), AccountFlowOpType.FREEZE, amount);
    }

    public MerchantSettleAccountEntity unfreeze(Long merchantId, String settleNo, BigDecimal amount) {
        if (accountFlowRepository.existsBySettleNoAndOpType(settleNo, AccountFlowOpType.UNFREEZE.getCode())) {
            return accountRepository.findByMerchantId(merchantId).orElseThrow();
        }
        return updateBalance(merchantId, null, settleNo, amount, AccountFlowOpType.UNFREEZE, amount.negate());
    }

    public MerchantSettleAccountEntity deductFrozen(Long merchantId, String settleNo, BigDecimal amount) {
        if (accountFlowRepository.existsBySettleNoAndOpType(settleNo, AccountFlowOpType.DEDUCT.getCode())) {
            return accountRepository.findByMerchantId(merchantId).orElseThrow();
        }
        return updateFrozenOnly(merchantId, settleNo, amount.negate(), AccountFlowOpType.DEDUCT);
    }

    public MerchantSettleAccountEntity debit(Long merchantId, String billNo, BigDecimal amount) {
        if (accountFlowRepository.existsByBillNoAndOpType(billNo, AccountFlowOpType.REFUND_DEBIT.getCode())) {
            return accountRepository.findByMerchantId(merchantId).orElseThrow();
        }
        return updateBalance(merchantId, billNo, null, amount.negate(), AccountFlowOpType.REFUND_DEBIT, BigDecimal.ZERO);
    }

    private MerchantSettleAccountEntity updateBalance(Long merchantId, String billNo, String settleNo,
                                                     BigDecimal waitDelta, AccountFlowOpType opType,
                                                     BigDecimal frozenDelta) {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < MAX_RETRY; i++) {
            MerchantSettleAccountEntity account = accountRepository.findByMerchantId(merchantId)
                    .orElseThrow(() -> BizException.of(ErrorCode.MERCHANT_INVALID));
            BigDecimal before = account.waitBalance;
            BigDecimal after = before.add(waitDelta);
            if (after.compareTo(BigDecimal.ZERO) < 0) {
                throw BizException.of(ErrorCode.INSUFFICIENT_BALANCE);
            }
            BigDecimal frozenAfter = account.frozenBalance.add(frozenDelta);
            if (frozenAfter.compareTo(BigDecimal.ZERO) < 0) {
                throw BizException.of(ErrorCode.INSUFFICIENT_BALANCE);
            }
            if (!accountRepository.updateBalanceCas(merchantId, waitDelta, frozenDelta, account.version, now)) {
                if (i == MAX_RETRY - 1) {
                    throw BizException.of(ErrorCode.CONCURRENT_UPDATE);
                }
                continue;
            }
            account.waitBalance = after;
            account.frozenBalance = frozenAfter;
            account.version = account.version + 1;
            saveFlow(merchantId, billNo, settleNo, opType, waitDelta.abs().max(frozenDelta.abs()), before, after);
            return account;
        }
        throw BizException.of(ErrorCode.CONCURRENT_UPDATE);
    }

    private MerchantSettleAccountEntity updateFrozenOnly(Long merchantId, String settleNo,
                                                         BigDecimal frozenDelta, AccountFlowOpType opType) {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < MAX_RETRY; i++) {
            MerchantSettleAccountEntity account = accountRepository.findByMerchantId(merchantId)
                    .orElseThrow(() -> BizException.of(ErrorCode.MERCHANT_INVALID));
            BigDecimal frozenAfter = account.frozenBalance.add(frozenDelta);
            if (frozenAfter.compareTo(BigDecimal.ZERO) < 0) {
                throw BizException.of(ErrorCode.INSUFFICIENT_BALANCE);
            }
            if (!accountRepository.updateFrozenCas(merchantId, frozenDelta, account.version, now)) {
                if (i == MAX_RETRY - 1) {
                    throw BizException.of(ErrorCode.CONCURRENT_UPDATE);
                }
                continue;
            }
            account.frozenBalance = frozenAfter;
            account.version = account.version + 1;
            saveFlow(merchantId, null, settleNo, opType, frozenDelta.abs(),
                    account.waitBalance, account.waitBalance);
            return account;
        }
        throw BizException.of(ErrorCode.CONCURRENT_UPDATE);
    }

    private void saveFlow(Long merchantId, String billNo, String settleNo, AccountFlowOpType opType,
                          BigDecimal amount, BigDecimal beforeBalance, BigDecimal afterBalance) {
        AccountFlowEntity flow = new AccountFlowEntity();
        flow.merchantId = merchantId;
        flow.billNo = billNo;
        flow.settleNo = settleNo;
        flow.opType = opType.getCode();
        flow.amount = amount;
        flow.beforeBalance = beforeBalance;
        flow.afterBalance = afterBalance;
        flow.createTime = LocalDateTime.now();
        accountFlowRepository.save(flow);
    }
}
