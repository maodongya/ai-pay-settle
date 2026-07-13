package com.payment.domain.service;

import com.payment.common.exception.BizException;
import com.payment.common.exception.ErrorCode;
import com.payment.domain.entity.BillRouteEntity;
import com.payment.domain.entity.SettleRouteEntity;
import com.payment.domain.repository.BillRouteRepository;
import com.payment.domain.repository.SettleRouteRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 分片路由服务：bill_no / settle_no → merchant_id，供数据层精准路由。
 */
@Service
public class ShardRouteService {

    private final BillRouteRepository billRouteRepository;
    private final SettleRouteRepository settleRouteRepository;

    public ShardRouteService(BillRouteRepository billRouteRepository,
                             SettleRouteRepository settleRouteRepository) {
        this.billRouteRepository = billRouteRepository;
        this.settleRouteRepository = settleRouteRepository;
    }

    public void registerBillRoute(String billNo, Long merchantId, Integer billType) {
        if (billRouteRepository.findByBillNo(billNo).isPresent()) {
            return;
        }
        BillRouteEntity route = new BillRouteEntity();
        route.billNo = billNo;
        route.merchantId = merchantId;
        route.billType = billType;
        route.createTime = LocalDateTime.now();
        billRouteRepository.save(route);
    }

    public void registerSettleRoute(String settleNo, Long merchantId) {
        if (settleRouteRepository.findBySettleNo(settleNo).isPresent()) {
            return;
        }
        SettleRouteEntity route = new SettleRouteEntity();
        route.settleNo = settleNo;
        route.merchantId = merchantId;
        route.createTime = LocalDateTime.now();
        settleRouteRepository.save(route);
    }

    public Optional<Long> findMerchantIdByBillNo(String billNo) {
        return billRouteRepository.findByBillNo(billNo).map(route -> route.merchantId);
    }

    public Long requireMerchantIdByBillNo(String billNo) {
        return findMerchantIdByBillNo(billNo)
                .orElseThrow(() -> BizException.of(ErrorCode.INVALID_PARAM, "bill route not found: " + billNo));
    }

    public Optional<Long> findMerchantIdBySettleNo(String settleNo) {
        return settleRouteRepository.findBySettleNo(settleNo).map(route -> route.merchantId);
    }
}
