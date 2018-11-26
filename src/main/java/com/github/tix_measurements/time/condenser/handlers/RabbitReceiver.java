package com.github.tix_measurements.time.condenser.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.github.tix_measurements.time.condenser.utils.jackson.TixPacketSerDe;
import com.github.tix_measurements.time.core.data.TixDataPacket;

@Component
public class RabbitReceiver {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	private final TixPacketSerDe packetSerDe;
	private final TixReceiver nextReceiver;
	
	public RabbitReceiver(TixReceiver nextReceiver) {
		logger.info("Creating RabbitReceiver");
		try {
			assertThat(nextReceiver).isNotNull();
		} catch (AssertionError ae) {
			throw new IllegalArgumentException(ae);
		}
		this.packetSerDe = new TixPacketSerDe();
		this.nextReceiver = nextReceiver;
	}
	
	@RabbitListener(queues = "${tix-condenser.queues.receiving.name}")
	public void receiveMessage(@Payload byte[] message) {
		logger.info("New message received");
		logger.trace("message={}", message);
		try {
			TixDataPacket packet = packetSerDe.deserialize(message);
			nextReceiver.receiveMessage(packet);
		} catch (IOException e) {
			logger.error("Message processing failed", e);
		}
	}
}
