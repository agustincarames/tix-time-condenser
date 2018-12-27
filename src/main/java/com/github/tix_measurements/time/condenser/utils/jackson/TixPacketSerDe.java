package com.github.tix_measurements.time.condenser.utils.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tix_measurements.time.core.data.TixDataPacket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TixPacketSerDe {
	private final ObjectMapper mapper;

	public TixPacketSerDe() {
		this.mapper = new ObjectMapper().addMixIn(TixDataPacket.class, TixDataPacketMixin.class);
	}

	public byte[] serialize(TixDataPacket packet) throws JsonProcessingException {
		return mapper.writeValueAsBytes(packet);
	}

	public byte[] serializeList(List<TixDataPacket> list) throws JsonProcessingException {
		return mapper.writeValueAsBytes(list);
	}

	public TixDataPacket deserialize(byte[] bytes) throws IOException {
		return mapper.readValue(bytes, TixDataPacket.class);
	}
	
	public static long getFirstReportTimestamp(TixDataPacket packet) {
		byte[] bytes = Arrays.copyOfRange(packet.getMessage(), 0, Long.BYTES);
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		buffer.put(bytes);
		buffer.flip();
		return buffer.getLong();
	}
	
	public static List<Long> getReportTimestamps(TixDataPacket packet) {
		byte[] message = packet.getMessage();
		ByteBuffer buffer = ByteBuffer.allocate(message.length);
		buffer.put(message);
		buffer.flip();
		
		List<Long> timestamps = new ArrayList<>(message.length / 45);
		for (int i = 0; i < message.length; i += 45) {
			timestamps.add(buffer.getLong(i));
		}
		return timestamps;
	}
}
