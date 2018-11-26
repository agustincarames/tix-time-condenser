package com.github.tix_measurements.time.condenser.store;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.tix_measurements.time.condenser.utils.jackson.TixPacketSerDe;
import com.github.tix_measurements.time.core.data.TixDataPacket;

@Component
public class MeasurementStore {
	private static final Set<PosixFilePermission> REPORTS_DIRECTORIES_PERMISSIONS = Collections.unmodifiableSet(PosixFilePermissions.fromString("rwxr-x---"));
	public static final String REPORTS_FILE_SUFFIX = "tix-report";
	public static final String REPORTS_FILE_EXTENSION = "json";
	public static final String REPORTS_FILE_NAME_TEMPLATE = format("%s-%%d.%s", REPORTS_FILE_SUFFIX, REPORTS_FILE_EXTENSION);

	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	private final TixPacketSerDe packetSerDe;
	private final Path baseReportsPath;
	
	public MeasurementStore(@Value("${tix-condenser.reports.path}") String reportsPath) {
		logger.info("Creating MeasurementStore");
		logger.trace("reportsPath={}", reportsPath);
		try {
			assertThat(reportsPath).isNotEmpty().isNotNull();
		} catch (AssertionError ae) {
			throw new IllegalArgumentException(ae);
		}
		this.packetSerDe = new TixPacketSerDe();
		this.baseReportsPath = Paths.get(reportsPath).toAbsolutePath();
	}
	
	public void storePacket(TixDataPacket packet) throws IOException {
		Path reportDirectory = generateReportPath(baseReportsPath, packet);
		logger.debug("reportDirectory={}", reportDirectory);
		if (!Files.exists(reportDirectory)) {
			logger.info("Creating reports directory");
			Files.createDirectories(reportDirectory,
					PosixFilePermissions.asFileAttribute(REPORTS_DIRECTORIES_PERMISSIONS));
		}
		long firstReportTimestamp = getFirstReportTimestamp(packet);
		Path reportPath = reportDirectory.resolve(format(REPORTS_FILE_NAME_TEMPLATE, firstReportTimestamp));
		if (!Files.exists(reportPath)) {
			Files.createFile(reportPath);
			logger.info("Creating report file");
			logger.debug("reportPath={}", reportPath);
			try (BufferedWriter writer = Files.newBufferedWriter(reportPath)) {
				writer.write(new String(packetSerDe.serialize(packet)));
			}
			logger.info("Report file successfully created");
		} else {
			logger.info("Report file already exists. Not writing to disk.");
			logger.info("reportPath={}", reportPath);
		}
	}

	public Path getBaseReportsPath() {
		return baseReportsPath;
	}

	public long getFirstReportTimestamp(TixDataPacket packet) {
		byte[] bytes = Arrays.copyOfRange(packet.getMessage(), 0, Long.BYTES);
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		buffer.put(bytes);
		buffer.flip();
		return buffer.getLong();
	}

	public static Path generateReportPath(Path baseReportsPath, TixDataPacket packet) {
		return baseReportsPath.resolve(Long.toString(packet.getUserId()))
				.resolve(Long.toString(packet.getInstallationId()));
	}
}
