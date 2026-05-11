package com.lhkeeper.ticketing.railway_ticketing.controller;

import com.lhkeeper.ticketing.railway_ticketing.common.annotation.RateLimit;
import com.lhkeeper.ticketing.railway_ticketing.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.TicketPageQueryReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.TicketPageQueryRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.service.TicketService;

import org.springframework.web.bind.annotation.RequestParam;



/**
 * 余票查询控制器
 */
@RestController
@RequestMapping("/ticket")
public class TicketController {

    @Autowired
    private TicketService ticketService;

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    /**
     * 余票分页查询，基于令牌桶限流
     */
    @RateLimit(key = "ticket:query", capacity = 1000, refillRate = 500.0)
    @GetMapping("/query")
    public Result<TicketPageQueryRespDTO> ticketQuery(TicketPageQueryReqDTO ticketPageQueryReqDTO) {
        return Result.success(ticketService.queryTicketByPage(ticketPageQueryReqDTO));
    }
    
    
}
