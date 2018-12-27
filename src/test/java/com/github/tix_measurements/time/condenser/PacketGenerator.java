package com.github.tix_measurements.time.condenser;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.github.tix_measurements.time.core.data.TixDataPacket;
import com.github.tix_measurements.time.core.data.TixPacketType;
import com.github.tix_measurements.time.core.util.TixCoreUtils;

public class PacketGenerator {
	public static final long DEFAULT_FIRST_UNIX_TIMESTAMP = 1530000000L;
	
	private static final long SECOND_TO_NANO = 1000 * 1000 * 1000;
	private static final long MILLI_TO_NANO = 1000 * 1000;
	
	private String from = "192.168.1.1";
	private String to = "10.0.0.1";
	private long initialTimestamp = DEFAULT_FIRST_UNIX_TIMESTAMP;
	private long userId = 1;
	private long installationId = 1;
	private KeyPair keyPair = null;
	private int reportsPerPacket = 60;
	
	public static PacketGenerator defaults() {
		return new PacketGenerator();
	}
	
	public PacketGenerator withFrom(String from) {
		this.from = from;
		return this;
	}
	
	public PacketGenerator withTo(String to) {
		this.to = to;
		return this;
	}
	
	public PacketGenerator withInitialTimestamp(long initialTimestamp) {
		this.initialTimestamp = initialTimestamp;
		return this;
	}
	
	public PacketGenerator withUserId(long userId) {
		this.userId = userId;
		return this;
	}
	
	public PacketGenerator withInstallationId(long installationId) {
		this.installationId = installationId;
		return this;
	}
	
	public PacketGenerator withKeyPair(KeyPair keyPair) {
		this.keyPair = keyPair;
		return this;
	}
	
	public PacketGenerator withReportsPerPacket(int reportsPerPacket) {
		this.reportsPerPacket = reportsPerPacket;
		return this;
	}
	
	public TixDataPacket build() {
		try {
			byte[] message = generateMessage(initialTimestamp, reportsPerPacket);
			if (keyPair == null) {
				keyPair = TixCoreUtils.NEW_KEY_PAIR.get();
			}
			TixDataPacket packet = new TixDataPacket(
					new InetSocketAddress(InetAddress.getByName(from), 4500),
					new InetSocketAddress(InetAddress.getByName(to), 4500),
					firstNanosOfNext(initialTimestamp),
					userId,
					installationId,
					keyPair.getPublic().getEncoded(),
					message,
					TixCoreUtils.sign(message, keyPair));
			packet.setReceptionTimestamp(firstNanosOfNext(initialTimestamp) + 5000 * MILLI_TO_NANO);
			return packet;
		} catch (UnknownHostException e) {
			throw new RuntimeException(e); 
		}
	}
	

	public static TixDataPacket createNewPacket(long userId, long installationId) {
		return PacketGenerator.defaults().withUserId(userId).withInstallationId(installationId).build();
	}
	
	private static long firstNanosOfCurrent(long startingUnixTimestamp) {
		LocalDateTime startOfDay = LocalDateTime
				.ofEpochSecond(startingUnixTimestamp, 0, ZoneOffset.UTC)
				.toLocalDate()
				.atStartOfDay();
		long secondsSinceStartOfDay = startingUnixTimestamp - startOfDay.toEpochSecond(ZoneOffset.UTC);
		return secondsSinceStartOfDay * SECOND_TO_NANO;
	}
	
	private static long firstNanosOfNext(long startingUnixTimestamp) {
		return firstNanosOfCurrent(startingUnixTimestamp) + 15 * SECOND_TO_NANO;
	}
	
	private static byte[] generateMessage(long startingUnixTimestamp, int reportsPerPacket) {
		long clock = firstNanosOfCurrent(startingUnixTimestamp);
		
		int unixTimestampSize = Long.BYTES;
		int packetTypeSize = Byte.BYTES;
		int packetSizeSize = Integer.BYTES;
		int timestamps = 4;
		int timestampSize = Long.BYTES;
		int rowSize = unixTimestampSize + packetTypeSize + packetSizeSize + timestampSize * timestamps;
		ByteBuffer messageBuffer = ByteBuffer.allocate(reportsPerPacket * rowSize);
		for (int i = 0; i < reportsPerPacket; i++) {
			messageBuffer.putLong(startingUnixTimestamp + i);
			char packetType = (i % 2 == 0 ? 'S' : 'L');
			messageBuffer.put((byte)packetType);
			messageBuffer.putInt((i % 2 == 0 ? TixPacketType.SHORT.getSize() : TixPacketType.LONG.getSize()));
			for (int j = 0; j < timestamps; j++) {
				messageBuffer.putLong(clock);
				clock += 5 * MILLI_TO_NANO;
			}
			clock += (1000 - 5 * timestamps) * MILLI_TO_NANO;
		}
		byte[] message = messageBuffer.array();
		return message;
	}
}
