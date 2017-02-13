package com.github.tix_measurements.time.condenser.utils.jackson;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class TixDataPacketMixin {
	@JsonIgnore
	abstract boolean isValid();
}
