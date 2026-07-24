package com.payment.api.service;

import com.payment.api.dto.ConsoleOverviewDTO;

/**
 * 控制台清算概览服务。
 */
public interface ConsoleDashboardService {

    /**
     * 查询整体清算概览数据。
     */
    ConsoleOverviewDTO getOverview();
}
