package com.lhkeeper.ticketing.railway_ticketing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 铁路售票系统启动入口
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.lhkeeper.ticketing.railway_ticketing.mapper")
public class RailwayTicketingApplication {

    public static void main(String[] args) {
        SpringApplication.run(RailwayTicketingApplication.class, args);
    }
}
