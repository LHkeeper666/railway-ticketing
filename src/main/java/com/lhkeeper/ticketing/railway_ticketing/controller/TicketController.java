package com.lhkeeper.ticketing.railway_ticketing.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.TicketPageQueryReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.service.TicketService;

import org.springframework.web.bind.annotation.RequestParam;



/**
 * <p>
 * 车票表 前端控制器
 * </p>
 *
 * @author jack
 * @since 2026-04-18
 */
@RestController
@RequestMapping("/ticket")
public class TicketController {

    @Autowired
    private TicketService ticketService;

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    @GetMapping("/query")
    public String ticketQuery(@RequestParam TicketPageQueryReqDTO ticketPageQueryReqDTO) {
        // TODO:
        ticketService.queryTicketByPage(ticketPageQueryReqDTO);
        return new String();
    }
    
    
}
