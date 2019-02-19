package com.github.tix_measurements.time.condenser.store;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
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
			logger.info("Installation {} check and extract starts with {}", getInstallationId(), sampleStartTimes().size());
			Optional<SubmittablePacketSet> extracted = checkAndExtractOnce();
			if (extracted.isPresent()) {
				return extracted;
			}
		} while (lastReportsCount != sampleStartTimes().size());
		logger.info("Installation {} check and extract ends with {}", getInstallationId(), sampleStartTimes().size());
		return Optional.empty();
	}
	
	static private int observationsIn(TixDataPacket packet) {
		return packet.getMessage().length / 45;
	}
	
	private Optional<Integer> find_gap_cut(List<TixDataPacket> packet) {
		long diff = MAX_ACCEPTED_REPORT_GAP * 1000000000L;
		Optional<Integer> gap_cut = Optional.empty();
		for (int i = 0; i + 1 < packet.size(); ++i) {
			final TixDataPacket prev = packet.get(i);
			final TixDataPacket next = packet.get(i + 1);
			final long next_diff = next.getInitialTimestamp() - prev.getInitialTimestamp(); 
			if (next_diff > diff) {
				diff = next_diff;
				gap_cut = Optional.of(i);
			}
		}
		return gap_cut;
	}
	
	protected Optional<SubmittablePacketSet> checkAndExtractOnce() throws IOException {
		List<TixDataPacket> processable_reports = new ArrayList<>();
		List<Long> remaining_start_times = new ArrayList<>(sampleStartTimes());
		
		while (processable_reports.stream().mapToInt(AbstractInstallationMeasurements::observationsIn).sum() < MIN_REQUIRED_REPORTS && !remaining_start_times.isEmpty()) {
			final long new_start = remaining_start_times.remove(0);
			final TixDataPacket new_report = this.get(Arrays.asList(new_start)).get(0);
			
			if (processable_reports.size() > 0) {
				final InetAddress processable_reports_ip = processable_reports.get(0).getFrom().getAddress();
				final InetAddress new_report_ip = new_report.getFrom().getAddress();
				if (!processable_reports_ip.equals(new_report_ip)) {
					logger.info("Installation {} dropping {} measures with wrong ip", getInstallationId(), processable_reports.size() + 1);
					this.delete(sampleStartTimes().subList(0, processable_reports.size() + 1));
					return Optional.empty();
				}
			}
			processable_reports.add(new_report);
			
			if (processable_reports.stream().mapToInt(AbstractInstallationMeasurements::observationsIn).sum() <= MIN_REQUIRED_REPORTS) {
				continue;
			}
			
			final Optional<Integer> gap_cut = find_gap_cut(processable_reports);
			final List<TixDataPacket> toSubmit;
			final List<Long> toDelete;
			if (gap_cut.isPresent()) {
				if (processable_reports.stream().limit(gap_cut.get()).mapToInt(AbstractInstallationMeasurements::observationsIn).sum() < MIN_REQUIRED_REPORTS) {
					logger.info("Installation {} dropping {} measures with too much separation", getInstallationId(), processable_reports.size() + 1);
					this.delete(sampleStartTimes().subList(0, gap_cut.get() + 1));
					return Optional.empty();
				} else {
					toSubmit = processable_reports.subList(0, gap_cut.get() + 1);
					toDelete = sampleStartTimes().subList(0, toSubmit.size() / 2);
				}
			} else {
				toSubmit = processable_reports;
				toDelete = sampleStartTimes().subList(0, toSubmit.size() / 2);
			}

			logger.info("Installation {} builds submittable packet set", getInstallationId());
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
		return Optional.empty();
	}
}
