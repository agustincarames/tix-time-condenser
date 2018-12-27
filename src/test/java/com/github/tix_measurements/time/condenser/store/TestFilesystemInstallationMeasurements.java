package com.github.tix_measurements.time.condenser.store;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.tix_measurements.time.condenser.PacketGenerator;
import com.github.tix_measurements.time.condenser.utils.jackson.TixPacketSerDe;
import com.github.tix_measurements.time.core.data.TixDataPacket;

public class TestFilesystemInstallationMeasurements {
	private static final long USER_ID = 1L;
	private static final long INSTALLATION_ID = 1L;

	private Path reportsPath;
	private TixDataPacket packet;
	private TixPacketSerDe serDe;
	
	@Before
	public void setup() throws IOException {
		reportsPath = Files.createTempDirectory("tix-test-temp");
		packet = PacketGenerator.createNewPacket(USER_ID, INSTALLATION_ID);
		serDe = new TixPacketSerDe();
	}

	@After
	public void teardown() throws IOException {
		if (reportsPath.toFile().exists()) {
			try (Stream<Path> files = Files.walk(reportsPath)) {
				files.sorted(Comparator.reverseOrder())
					.map(Path::toFile)
					.peek(System.out::println)
					.forEach(File::delete);
			}
		}
	}
	
	@Test
	public void testPacketCorrectlyStored() throws IOException {
		FilesystemInstallationMeasurements measurements = new FilesystemInstallationMeasurements(reportsPath, USER_ID, INSTALLATION_ID);
		
		long count = Files.walk(reportsPath).count(); 

		measurements.append(packet);

		assertThat(measurements.sampleStartTimes().contains(PacketGenerator.DEFAULT_FIRST_UNIX_TIMESTAMP));
		assertThat(Files.walk(reportsPath).count()).isEqualTo(count + 3);
		
		try (Stream<Path> paths = Files.walk(reportsPath)) {
			paths.forEach(file -> {
				if (file.toFile().isDirectory()) {
					return;
				}
				
				assertThat(file.getFileName().toString())
						.startsWith(FilesystemInstallationMeasurements.REPORTS_FILE_SUFFIX)
						.endsWith(FilesystemInstallationMeasurements.REPORTS_FILE_EXTENSION);
				assertThat(file.getFileName().toString())
						.isEqualTo(format(FilesystemInstallationMeasurements.REPORTS_FILE_NAME_TEMPLATE, PacketGenerator.DEFAULT_FIRST_UNIX_TIMESTAMP));

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
