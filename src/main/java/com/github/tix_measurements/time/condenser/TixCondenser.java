package com.github.tix_measurements.time.condenser;

import com.github.tix_measurements.time.condenser.handlers.Receiver;
import com.github.tix_measurements.time.condenser.handlers.Sender;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableRabbit
public class TixCondenser {

	@Value("${tix-condenser.queues.receiving.name}")
	private String receivingQueueName;

	@Value("${tix-condenser.queues.new-report.name}")
	private String newReportQueueName;

	@Value("${tix-condenser.reports.path}")
	private String reportsPath;

	public static void main(String[] args) {
		SpringApplication.run(TixCondenser.class, args);
	}

	@Bean
	Sender sender(RabbitTemplate template) {
		return new Sender(template);
	}
}
