package com.github.tix_measurements.time.condenser.handlers;

import com.github.tix_measurements.time.condenser.store.MeasurementStore;
import com.github.tix_measurements.time.condenser.utils.jackson.TixPacketSerDe;
import com.github.tix_measurements.time.core.data.TixDataPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@Component
public class TixReceiver {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	private final TixPacketSerDe packetSerDe;
	private final MeasurementStore measurementStore;
	private final TixPackageValidator packageValidator;

	public TixReceiver(MeasurementStore measurementStore,
		               TixPackageValidator packageValidator) {
		logger.info("Creating TixReceiver");
		try {
			assertThat(measurementStore).isNotNull();
			assertThat(packageValidator).isNotNull();
		} catch (AssertionError ae) {
			throw new IllegalArgumentException(ae);
		}
		this.packetSerDe = new TixPacketSerDe();
		this.measurementStore = measurementStore;
		this.packageValidator = packageValidator;
	}

	@RabbitListener(queues = "${tix-condenser.queues.receiving.name}")
	public void receiveMessage(@Payload byte[] message) {
		logger.info("New message received");
		logger.trace("message={}", message);
		try {
			final TixDataPacket packet = packetSerDe.deserialize(message);
			if (!packet.isValid()) {
				logger.warn("Invalid packet");
				logger.debug("packet={}", packet);
				return;
			}

			if (!packageValidator.validUserAndInstallation(packet)) {
				logger.warn("Invalid user or installation");
				logger.debug("packet={}", packet);
				return;
			}

			logger.info("New valid packet received");
			logger.debug("packet={}", packet);
			measurementStore.storePacket(packet);
		} catch (IOException ioe) {
			logger.error("Exception caught", ioe);
			throw new IllegalArgumentException(ioe);
		}
	}
}
