package com.github.tix_measurements.time.condenser.handlers;

import com.github.tix_measurements.time.condenser.sender.RabbitSubmitter;
import com.github.tix_measurements.time.condenser.sender.SubmittablePacketSet;
import com.github.tix_measurements.time.condenser.store.MeasurementStore;
import com.github.tix_measurements.time.core.data.TixDataPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Component
public class TixReceiver implements ApplicationListener<ContextRefreshedEvent> {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	private final MeasurementStore measurementStore;
	private final TixPacketValidator packetValidator;
	private final RabbitSubmitter submitter;

	public TixReceiver(MeasurementStore measurementStore,
		               TixPacketValidator packetValidator,
		               RabbitSubmitter submitter) {
		logger.info("Creating TixReceiver");
		try {
			assertThat(measurementStore).isNotNull();
			assertThat(packetValidator).isNotNull();
			assertThat(submitter).isNotNull();
		} catch (AssertionError ae) {
			throw new IllegalArgumentException(ae);
		}
		this.measurementStore = measurementStore;
		this.packetValidator = packetValidator;
		this.submitter = submitter;
	}

	@Override
	public void onApplicationEvent(ContextRefreshedEvent arg0) {
		try {
			for (SubmittablePacketSet measures: measurementStore.packetsToSend()) {
				submitter.send(measures);
			}
		} catch (Exception e) {
			// TODO: Do something
		}
	}

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
		
		Optional<SubmittablePacketSet> toSend = measurementStore.storePacket(packet);
		if (toSend.isPresent()) {
			submitter.send(toSend.get());
		}
	}
}
