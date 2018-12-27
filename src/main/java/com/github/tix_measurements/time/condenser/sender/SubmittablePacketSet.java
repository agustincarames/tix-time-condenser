package com.github.tix_measurements.time.condenser.sender;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.github.tix_measurements.time.core.data.TixDataPacket;

public interface SubmittablePacketSet {
	String getId();
	
	List<TixDataPacket> packetsToSubmit() throws IOException;
	
	Optional<SubmittablePacketSet> onSubmitSuccess() throws IOException;
}
