package com.github.tix_measurements.time.condenser.store;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.tix_measurements.time.condenser.sender.SubmittablePacketSet;
import com.github.tix_measurements.time.condenser.utils.jackson.TixPacketSerDe;
import com.github.tix_measurements.time.core.data.TixDataPacket;

public abstract class AbstractInstallationMeasurements implements InstallationMeasurements {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private static final int MIN_REQUIRED_REPORTS = 1024 + 60;
	private static final int MAX_REPORTS_USED_AT_ONCE = 1200;
	private static final int MAX_ACCEPTED_REPORT_GAP = 5 * 60;
	private static final int MAX_MEASURES_PER_PACKET = 60;

	public Optional<SubmittablePacketSet> checkAndExtract() throws IOException {
		int lastReportsCount;
		do {
			lastReportsCount = sampleStartTimes().size();
			logger.debug("Installation {} check and extract starts with {}", getInstallationId(), sampleStartTimes().size());
			Optional<SubmittablePacketSet> extracted = checkAndExtractOnce();
			if (extracted.isPresent()) {
				return extracted;
			}
		} while (lastReportsCount != sampleStartTimes().size());
		logger.debug("Installation {} check and extract ends with {}", getInstallationId(), sampleStartTimes().size());
		return Optional.empty();
	}
	
	protected Optional<SubmittablePacketSet> checkAndExtractOnce() throws IOException {
		final int initialNumberOfPackets = Math.min(sampleStartTimes().size(), MAX_REPORTS_USED_AT_ONCE / MAX_MEASURES_PER_PACKET);

		List<Long> availableStartTimes = sampleStartTimes().subList(0, Math.max(0, initialNumberOfPackets));
		for (int i = 0; i + 1 < availableStartTimes.size(); i++) {
			if (availableStartTimes.get(i) + MAX_ACCEPTED_REPORT_GAP >= availableStartTimes.get(i + 1)) {
				continue;
			}
			
			if ((i + 1) * MAX_MEASURES_PER_PACKET < MIN_REQUIRED_REPORTS) {
				// This set is too small and can't grow
				logger.info("Installation {} dropping {} start time measures", getInstallationId(), i + 1);
				delete(availableStartTimes.subList(0, i + 1));
				return Optional.empty();
			} else {
				// This set might be large enough but can't grow
				logger.info("Installation {} restricted to {} start time measures", getInstallationId(), i + 1);
				availableStartTimes = availableStartTimes.subList(0, i + 1);
				break;
			}
		}
		if (availableStartTimes.size() * MAX_MEASURES_PER_PACKET < MIN_REQUIRED_REPORTS) {
			// This set is too small and may grow later
			logger.info("Installation {} delayed with {} start time measures", getInstallationId(), availableStartTimes.size());
			return Optional.empty();
		}

		
		List<TixDataPacket> packetsAvailable = get(availableStartTimes);
		for (int i = 0; i + 1 < packetsAvailable.size(); i++) {
			if (packetsAvailable.get(i).getFrom().equals(packetsAvailable.get(0).getFrom())) {
				continue;
			}
			
			if ((i + 1) * MAX_MEASURES_PER_PACKET < MIN_REQUIRED_REPORTS) {
				// This set is too small and can't grow
				logger.info("Installation {} dropping {} packets", getInstallationId(), i + 1);
				delete(availableStartTimes.subList(0, i + 1));
				return Optional.empty();
			} else {
				// This set might be large enough but can't grow
				logger.info("Installation {} restricted to {} packets", getInstallationId(), i + 1);
				availableStartTimes = availableStartTimes.subList(0, i + 1);
				packetsAvailable = packetsAvailable.subList(0, i + 1);
				break;
			}
		}
		if (packetsAvailable.size() * MAX_MEASURES_PER_PACKET < MIN_REQUIRED_REPORTS) {
			// This set is too small and may grow later
			logger.info("Installation {} delayed with {} packets", getInstallationId(), availableStartTimes.size());
			return Optional.empty();
		}

		List<Long> timestampsAvailable = packetsAvailable.stream()
				.map(TixPacketSerDe::getReportTimestamps)
				.flatMap(List<Long>::stream)
				.collect(Collectors.toList());
		if (timestampsAvailable.size() < MIN_REQUIRED_REPORTS) {
			logger.info("Installation {} delayed with {} measures", getInstallationId(), timestampsAvailable);
			return Optional.empty();
		}
		
		final List<TixDataPacket> toSubmit = packetsAvailable;
		final List<Long> toDelete = toSubmit.stream()
				.limit(toSubmit.size() / 2)
				.map(TixPacketSerDe::getFirstReportTimestamp)
				.collect(Collectors.toList());

		logger.info("Installation {} extracts", getInstallationId());
		return Optional.of(new SubmittablePacketSet() {
			@Override
			public List<TixDataPacket> packetsToSubmit() {
				return toSubmit;
			}

			@Override
			public Optional<SubmittablePacketSet> onSubmitSuccess() throws IOException {
				logger.info("Installation {} did submit", getId());
				delete(toDelete);
				return checkAndExtract();
			}

			@Override
			public String getId() {
				return Long.toString(getInstallationId());
			}
		});
	}
}
