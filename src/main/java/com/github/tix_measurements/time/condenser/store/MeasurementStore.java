package com.github.tix_measurements.time.condenser.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.tix_measurements.time.condenser.sender.SubmittablePacketSet;
import com.github.tix_measurements.time.core.data.TixDataPacket;

@Component
public class MeasurementStore {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	private final Path baseReportsPath;
	
	private Map<Long, InstallationMeasurements> content;
	
	public MeasurementStore(@Value("${tix-condenser.reports.path}") String reportsPath) {
		logger.info("Creating MeasurementStore");
		logger.trace("reportsPath={}", reportsPath);
		try {
			assertThat(reportsPath).isNotEmpty().isNotNull();
		} catch (AssertionError ae) {
			throw new IllegalArgumentException(ae);
		}
		this.baseReportsPath = Paths.get(reportsPath).toAbsolutePath();

		try {
			this.content = FilesystemInstallationMeasurements.loadFrom(baseReportsPath);
		} catch (IOException e) {
			this.content = new HashMap<>();
		}
	}
	
	public Optional<SubmittablePacketSet> storePacket(TixDataPacket packet) throws IOException {
		Long userId = packet.getUserId();
		Long installationId = packet.getInstallationId();
		
		if (!content.containsKey(installationId)) {
			InstallationMeasurements measures = new FilesystemInstallationMeasurements(baseReportsPath, userId, installationId);
			content.put(installationId, measures);
		}
		InstallationMeasurements measures = content.get(installationId);
		measures.append(packet);
		return measures.checkAndExtract();
	}
	
	public List<SubmittablePacketSet> packetsToSend() throws IOException {
		List<SubmittablePacketSet> toSend = new ArrayList<>();
		for (InstallationMeasurements measures: content.values()) {
			measures.checkAndExtract().ifPresent(toSend::add);
		}
		return toSend;
	}
}
