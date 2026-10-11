package com.auction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AuctionApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuctionApiApplication.class, args);
	}

}
