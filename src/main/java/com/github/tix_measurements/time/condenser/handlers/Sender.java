package com.github.tix_measurements.time.condenser.handlers;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

public class Sender {

	private RabbitTemplate template;

	@Autowired
	public Sender(RabbitTemplate template) {
		this.template = template;
	}

	public void send(Path reportPath) {
		this.template.convertAndSend(reportPath.toString());
	}
}
