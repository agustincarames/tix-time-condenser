package com.github.tix_measurements.time.condenser;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableRabbit
public class TixCondenser {
	public static void main(String[] args) {
		SpringApplication.run(TixCondenser.class, args);
	}
}
