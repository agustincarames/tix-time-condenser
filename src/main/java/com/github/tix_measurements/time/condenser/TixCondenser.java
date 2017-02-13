package com.github.tix_measurements.time.condenser;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableRabbit
public class TixCondenser {

	@Value("${tix-condenser.queues.receiving.name}")
	private String receivingQueueName;

	@Value("${tix-condenser.reports.path}")
	private String reportsPath;

	public static void main(String[] args) {
		SpringApplication.run(TixCondenser.class, args);
	}
}
