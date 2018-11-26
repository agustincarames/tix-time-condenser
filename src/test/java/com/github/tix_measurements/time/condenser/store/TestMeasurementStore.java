package com.github.tix_measurements.time.condenser.store;

import com.github.tix_measurements.time.condenser.PackageGenerator;
import com.github.tix_measurements.time.condenser.utils.jackson.TixPacketSerDe;
import com.github.tix_measurements.time.core.data.TixDataPacket;
import com.github.tix_measurements.time.core.util.TixCoreUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.Comparator;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class TestMeasurementStore {
	private static final Path REPORTS_PATH = Paths.get(System.getProperty("java.io.tmpdir"));
	private static final long USER_ID = 1L;
	private static final long INSTALLATION_ID = 1L;
	private static final KeyPair INSTALLATION_KEY_PAIR = TixCoreUtils.NEW_KEY_PAIR.get();

	private MeasurementStore measurementStore;
	private TixDataPacket packet;
	private TixPacketSerDe serDe;
	
	@Before
	public void setup() throws IOException, InterruptedException {
		measurementStore = new MeasurementStore(REPORTS_PATH.toString());
		packet = PackageGenerator.createNewPacket(USER_ID, INSTALLATION_ID, INSTALLATION_KEY_PAIR);
		serDe = new TixPacketSerDe();
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
	public void testPacketCorrectlyStored() throws IOException, InterruptedException {
		Path expectedReportPath = MeasurementStore.generateReportPath(REPORTS_PATH, packet);
		long count = Files.exists(expectedReportPath) 
				? Files.walk(expectedReportPath).count() 
				: 0;
		
		measurementStore.storePacket(packet);
		assertThat(Files.walk(expectedReportPath).count()).isEqualTo(count + 2);
		
		try (Stream<Path> paths = Files.walk(expectedReportPath)) {
			paths.forEach(file -> {
				if (file.equals(expectedReportPath)) {
					return;
				}
				assertThat(file)
						.exists()
						.isRegularFile();
				assertThat(file.getFileName().toString())
						.startsWith(MeasurementStore.REPORTS_FILE_SUFFIX)
						.endsWith(MeasurementStore.REPORTS_FILE_EXTENSION);
				assertThat(file.getFileName().toString())
						.isEqualTo(format(MeasurementStore.REPORTS_FILE_NAME_TEMPLATE, PackageGenerator.FIRST_UNIX_TIMESTAMP));
				try (BufferedReader reader = Files.newBufferedReader(file)) {
					assertThat(reader.lines().count()).isEqualTo(1);
					reader.lines().forEach(reportLine -> {
						try {
							TixDataPacket filePacket = serDe.deserialize(reportLine.getBytes());
							assertThat(filePacket).isEqualTo(packet);
						} catch (IOException e) {
							throw new AssertionError(e);
						}
					});
				} catch (IOException e) {
					throw new AssertionError(e);
				}
			});
		}
	}
}
