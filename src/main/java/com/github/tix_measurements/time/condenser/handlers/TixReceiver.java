package com.github.tix_measurements.time.condenser.handlers;

import com.github.tix_measurements.time.condenser.store.MeasurementStore;
import com.github.tix_measurements.time.core.data.TixDataPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@Component
public class TixReceiver {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	private final MeasurementStore measurementStore;
	private final TixPacketValidator packetValidator;

	public TixReceiver(MeasurementStore measurementStore,
		               TixPacketValidator packetValidator) {
		logger.info("Creating TixReceiver");
		try {
			assertThat(measurementStore).isNotNull();
			assertThat(packetValidator).isNotNull();
		} catch (AssertionError ae) {
			throw new IllegalArgumentException(ae);
		}
		this.measurementStore = measurementStore;
		this.packetValidator = packetValidator;
	}

	@RabbitListener(queues = "${tix-condenser.queues.receiving.name}")
	public void receiveMessage(TixDataPacket packet) throws IOException {
		if (!packet.isValid()) {
			logger.warn("Invalid packet");
			logger.debug("packet={}", packet);
			return;
		}

		if (!packetValidator.validUserAndInstallation(packet)) {
			logger.warn("Invalid user or installation");
			logger.debug("packet={}", packet);
			return;
		}

		logger.info("New valid packet received");
		logger.debug("packet={}", packet);
		measurementStore.storePacket(packet);
	}
}
