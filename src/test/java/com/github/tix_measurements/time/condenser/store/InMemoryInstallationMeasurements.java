package com.github.tix_measurements.time.condenser.store;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.github.tix_measurements.time.condenser.utils.jackson.TixPacketSerDe;
import com.github.tix_measurements.time.core.data.TixDataPacket;

public class InMemoryInstallationMeasurements extends AbstractInstallationMeasurements {
	private long installationId;
	private List<TixDataPacket> packets;
	
	public InMemoryInstallationMeasurements(long installationId, List<TixDataPacket> packets) {
		this.installationId = installationId;
		this.packets = new ArrayList<>(packets);
	}

	@Override
	public long getInstallationId() {
		return installationId;
	}

	@Override
	public List<Long> sampleStartTimes() {
		return packets.stream()
				.map(TixPacketSerDe::getFirstReportTimestamp)
				.collect(Collectors.toList());
	}

	@Override
	public void append(TixDataPacket packet) throws IOException {
		packets.add(packet);
	}

	@Override
	public List<TixDataPacket> get(List<Long> range) throws IOException {
		return packets.stream()
				.filter(packet -> range.contains(TixPacketSerDe.getFirstReportTimestamp(packet)))
				.collect(Collectors.toList());
	}

	@Override
	public void delete(List<Long> range) throws IOException {
		packets.removeIf(packet -> range.contains(TixPacketSerDe.getFirstReportTimestamp(packet)));
	}
}
