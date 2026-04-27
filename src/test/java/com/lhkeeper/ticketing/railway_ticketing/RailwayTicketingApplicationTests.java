package com.lhkeeper.ticketing.railway_ticketing;

import com.lhkeeper.ticketing.railway_ticketing.service.RedisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RailwayTicketingApplicationTests {

	@Autowired
	private final RedisService redisService;

    RailwayTicketingApplicationTests(RedisService redisService) {
        this.redisService = redisService;
    }

    @Test
	void contextLoads() {
		redisService.set("test", "test");
		String test = (String) redisService.get("test");
		System.out.println(test);


	}

}
