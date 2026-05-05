package com.lhkeeper.ticketing.railway_ticketing.service;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.TicketPageQueryReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.TicketPageQueryRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Ticket;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 余票查询服务接口
 */
public interface TicketService extends IService<Ticket> {

    /**
     * 分页查询余票信息，含缓存和分布式锁
     */
    TicketPageQueryRespDTO queryTicketByPage(TicketPageQueryReqDTO ticketPageQueryReqDTO);

}
