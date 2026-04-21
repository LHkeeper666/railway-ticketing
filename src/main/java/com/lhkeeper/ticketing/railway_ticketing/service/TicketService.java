package com.lhkeeper.ticketing.railway_ticketing.service;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.TicketPageQueryReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.TicketPageQueryRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Ticket;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 车票表 服务类
 * </p>
 *
 * @author jack
 * @since 2026-04-18
 */
public interface TicketService extends IService<Ticket> {

    TicketPageQueryRespDTO queryTicketByPage(TicketPageQueryReqDTO ticketPageQueryReqDTO);

}
