package com.github.tix_measurements.time.condenser.store;

import com.github.tix_measurements.time.core.data.TixDataPacket;
import com.github.tix_measurements.time.core.data.TixPacketType;
import com.github.tix_measurements.time.core.util.TixCoreUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class TestMeasurementStore {
	private static final Path REPORTS_PATH = Paths.get(System.getProperty("java.io.tmpdir"));
	private static final long USER_ID = 1L;
	private static final long INSTALLATION_ID = 1L;
	private static final KeyPair INSTALLATION_KEY_PAIR = TixCoreUtils.NEW_KEY_PAIR.get();

	private MeasurementStore measurementStore;
	private TixDataPacket packet;
	
	public static byte[] generateMessage() throws InterruptedException {
		int reports = 10;
		int unixTimestampSize = Long.BYTES;
		int packetTypeSize = Character.BYTES;
		int packetSizeSize = Integer.BYTES;
		int timestamps = 4;
		int timestampSize = Long.BYTES;
		int rowSize = unixTimestampSize + packetTypeSize + packetSizeSize + timestampSize * timestamps;
		ByteBuffer messageBuffer = ByteBuffer.allocate(reports * rowSize);
		for (int i = 0; i < reports; i++) {
			messageBuffer.putLong(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
			char packetType = (i % 2 == 0 ? 'S' : 'L');
			messageBuffer.put((byte)packetType);
			messageBuffer.putInt((i % 2 == 0 ? TixPacketType.SHORT.getSize() : TixPacketType.LONG.getSize()));
			for (int j = 0; j < timestamps; j++) {
				messageBuffer.putLong(TixCoreUtils.NANOS_OF_DAY.get());
				Thread.sleep(5L);
			}
			Thread.sleep(1000L - 5L * timestamps);
		}
		byte[] message = messageBuffer.array();
		return message;
	}

	public static long getReportFirstUnixTimestamp(byte[] message) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		byte[] bytes = Arrays.copyOfRange(message, 0, Long.BYTES);
		buffer.put(bytes);
		buffer.flip();
		return buffer.getLong();
	}

	@Before
	public void setup() throws IOException, InterruptedException {
		measurementStore = new MeasurementStore(REPORTS_PATH.toString());
		
		byte[] message = generateMessage();
		long startTime = TixCoreUtils.NANOS_OF_DAY.get();
		
		packet = new TixDataPacket(
				new InetSocketAddress(InetAddress.getLocalHost(), 4500),
				new InetSocketAddress(InetAddress.getByName("8.8.8.8"), 4500),
				startTime,
				USER_ID,
				INSTALLATION_ID,
				INSTALLATION_KEY_PAIR.getPublic().getEncoded(),
				message,
				TixCoreUtils.sign(message, INSTALLATION_KEY_PAIR));
		packet.setReceptionTimestamp(5123123 + startTime);
	}

	@After
	public void teardown() throws IOException {
		if (REPORTS_PATH.resolve(Long.toString(USER_ID)).toFile().exists()) {
			Files.walk(REPORTS_PATH.resolve(Long.toString(USER_ID)))
					.sorted(Comparator.reverseOrder())
					.map(Path::toFile)
					.peek(System.out::println)
					.forEach(File::delete);
		}
	}

	@Test
	public void testConstructor() {
		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> new MeasurementStore(null));
		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> new MeasurementStore(""));
	}

	@Test
	public void testValidPacket() throws IOException, InterruptedException {
		Path expectedReportPath = MeasurementStore.generateReportPath(REPORTS_PATH, packet);
		long count = Files.exists(expectedReportPath) 
				? Files.walk(expectedReportPath).count() 
				: 0;
		
		measurementStore.storePacket(packet);
		assertThat(Files.walk(expectedReportPath).count()).isEqualTo(count + 2);
	}
}
