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
	public static final long FIRST_UNIX_TIMESTAMP = 1530000000L;
	
	private static final long SECOND_TO_NANO = 1000 * 1000 * 1000;
	private static final long MILLI_TO_NANO = 1000 * 1000;
	
	private static long firstNanosOfCurrent() {
		LocalDateTime startOfDay = LocalDateTime
				.ofEpochSecond(FIRST_UNIX_TIMESTAMP, 0, ZoneOffset.UTC)
				.toLocalDate()
				.atStartOfDay();
		long secondsSinceStartOfDay = FIRST_UNIX_TIMESTAMP - startOfDay.toEpochSecond(ZoneOffset.UTC);
		return secondsSinceStartOfDay * SECOND_TO_NANO;
	}
	
	private static long firstNanosOfNext() {
		return firstNanosOfCurrent() + 15 * SECOND_TO_NANO;
	}

	public static TixDataPacket createNewPacket(long userId, long installationId, KeyPair keyPair) throws UnknownHostException, InterruptedException {
		byte[] message = generateMessage();
		TixDataPacket packet = new TixDataPacket(
				new InetSocketAddress(InetAddress.getLocalHost(), 4500),
				new InetSocketAddress(InetAddress.getByName("8.8.8.8"), 4500),
				firstNanosOfNext(),
				userId,
				installationId,
				keyPair.getPublic().getEncoded(),
				message,
				TixCoreUtils.sign(message, keyPair));
		packet.setReceptionTimestamp(firstNanosOfNext() + 5000 * MILLI_TO_NANO);
		return packet;
	}
	
	private static byte[] generateMessage() throws InterruptedException {
		long clock = firstNanosOfCurrent();
		
		int reports = 10;
		int unixTimestampSize = Long.BYTES;
		int packetTypeSize = Character.BYTES;
		int packetSizeSize = Integer.BYTES;
		int timestamps = 4;
		int timestampSize = Long.BYTES;
		int rowSize = unixTimestampSize + packetTypeSize + packetSizeSize + timestampSize * timestamps;
		ByteBuffer messageBuffer = ByteBuffer.allocate(reports * rowSize);
		for (int i = 0; i < reports; i++) {
			messageBuffer.putLong(FIRST_UNIX_TIMESTAMP);
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
