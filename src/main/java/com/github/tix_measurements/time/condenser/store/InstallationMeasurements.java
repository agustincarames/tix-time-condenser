package com.github.tix_measurements.time.condenser.store;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.github.tix_measurements.time.condenser.sender.SubmittablePacketSet;
import com.github.tix_measurements.time.core.data.TixDataPacket;

/** A container of {@link TixDataPacket}s associated to a single installation */
interface InstallationMeasurements {
	long getInstallationId();
	
	List<Long> sampleStartTimes();
	
	/** Adds a data packet to the store */
	void append(TixDataPacket packet) throws IOException;
	
	/** Reads available data for a time range */
	List<TixDataPacket> get(List<Long> range) throws IOException;
	
	/** Removes data in a time range */
	void delete(List<Long> range) throws IOException;
	
	Optional<SubmittablePacketSet> checkAndExtract() throws IOException;
}
