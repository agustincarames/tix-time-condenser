package com.github.tix_measurements.time.condenser.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.github.tix_measurements.time.condenser.PacketGenerator;
import com.github.tix_measurements.time.condenser.sender.SubmittablePacketSet;

public class TestAbstractInstallationMeasurements {
	private static final long INSTALLATION_ID = 1L;
	
	private InstallationMeasurements measures;
	
	@Before
	public void setup() throws IOException {
		this.measures = new InMemoryInstallationMeasurements(INSTALLATION_ID, Arrays.asList());
	}

	@Test
	public void testEmptyMeasurements() throws IOException {
		assertThat(measures.checkAndExtract()).isEqualTo(Optional.empty());
	}

	@Test
	public void testTooFewPacketsGrowable() throws IOException {
		for (int i = 0; i < 16; ++i) {
			long timestamp = PacketGenerator.DEFAULT_FIRST_UNIX_TIMESTAMP + 60 * i;
			measures.append(PacketGenerator.defaults().withInitialTimestamp(timestamp).build());
		}
		int initialCount = measures.sampleStartTimes().size();
		assertThat(measures.checkAndExtract()).isEqualTo(Optional.empty());
		assertThat(measures.sampleStartTimes().size()).isEqualTo(initialCount);
	}

	@Test
	public void testTooFewPacketsWithNoGrowth() throws IOException {
		for (int i = 0; i < 16; ++i) {
			long timestamp = PacketGenerator.DEFAULT_FIRST_UNIX_TIMESTAMP + 60 * i;
			measures.append(PacketGenerator.defaults().withInitialTimestamp(timestamp).build());
		}
		measures.append(PacketGenerator.defaults().withInitialTimestamp(PacketGenerator.DEFAULT_FIRST_UNIX_TIMESTAMP + 100000).build());
		
		assertThat(measures.checkAndExtract()).isEqualTo(Optional.empty());
		assertThat(measures.sampleStartTimes().size()).isEqualTo(1);
	}

	@Test
	public void testEnoughMeasurementsWithNoGrowth() throws IOException {
		for (int i = 0; i < 19; ++i) {
			long timestamp = PacketGenerator.DEFAULT_FIRST_UNIX_TIMESTAMP + 60 * i;
			measures.append(PacketGenerator.defaults().withInitialTimestamp(timestamp).build());
		}
		measures.append(PacketGenerator.defaults().withInitialTimestamp(PacketGenerator.DEFAULT_FIRST_UNIX_TIMESTAMP + 100000).build());
		
		Optional<SubmittablePacketSet> optionalPacketSet = measures.checkAndExtract();
		assertThat(optionalPacketSet.isPresent());
		assertThat(measures.sampleStartTimes().size()).isEqualTo(20);

		SubmittablePacketSet packetSet = optionalPacketSet.get();		
		assertThat(packetSet.packetsToSubmit().size()).isEqualTo(19);
		
		packetSet.onSubmitSuccess();
		assertThat(measures.sampleStartTimes().size()).isEqualTo(11);
	}
}
