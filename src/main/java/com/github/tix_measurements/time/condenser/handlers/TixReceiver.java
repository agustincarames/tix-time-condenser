package com.github.tix_measurements.time.condenser.handlers;

import com.github.tix_measurements.time.condenser.utils.jackson.TixPacketSerDe;
import com.github.tix_measurements.time.core.data.TixDataPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

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

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

@Component
public class TixReceiver {
	private static final Set<PosixFilePermission> REPORTS_DIRECTORIES_PERMISSIONS = Collections.unmodifiableSet(PosixFilePermissions.fromString("rwxr-x---"));
	public static final String REPORTS_FILE_SUFFIX = "tix-report";
	public static final String REPORTS_FILE_EXTENSION = "json";
	public static final String REPORTS_FILE_NAME_TEMPLATE = format("%s-%%d.%s", REPORTS_FILE_SUFFIX, REPORTS_FILE_EXTENSION);

	private final Logger logger = LoggerFactory.getLogger(TixReceiver.class);
	private final TixPacketSerDe packetSerDe;
	private final Path baseReportsPath;
	private final TixPackageValidator packageValidator;

	public TixReceiver(@Value("${tix-condenser.reports.path}") String reportsPath,
		               TixPackageValidator packageValidator) {
		logger.info("Creating TixReceiver");
		logger.trace("reportsPath={}", reportsPath);
		try {
			assertThat(reportsPath).isNotEmpty().isNotNull();
			assertThat(packageValidator).isNotNull();
		} catch (AssertionError ae) {
			throw new IllegalArgumentException(ae);
		}
		this.packetSerDe = new TixPacketSerDe();
		this.baseReportsPath = Paths.get(reportsPath).toAbsolutePath();
		this.packageValidator = packageValidator;
	}

	public static Path generateReportPath(Path baseReportsPath, TixDataPacket packet) {
		return baseReportsPath.resolve(Long.toString(packet.getUserId()))
				.resolve(Long.toString(packet.getInstallationId()));
	}

	@RabbitListener(queues = "${tix-condenser.queues.receiving.name}")
	public void receiveMessage(@Payload byte[] message) {
		logger.info("New message received");
		logger.trace("message={}", message);
		try {
			final TixDataPacket packet = packetSerDe.deserialize(message);
			if (!packet.isValid()) {
				logger.warn("Invalid packet");
				logger.debug("packet={}", packet);
				return;
			}

			if (!packageValidator.validUserAndInstallation(packet)) {
				logger.warn("Invalid user or installation");
				logger.debug("packet={}", packet);
				return;
			}

			logger.info("New valid packet received");
			logger.debug("packet={}", packet);
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
		} catch (IOException ioe) {
			logger.error("Exception caught", ioe);
			throw new IllegalArgumentException(ioe);
		}
	}

	public long getFirstReportTimestamp(TixDataPacket packet) {
		byte[] bytes = Arrays.copyOfRange(packet.getMessage(), 0, Long.BYTES);
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		buffer.put(bytes);
		buffer.flip();
		return buffer.getLong();
	}

	public Path getBaseReportsPath() {
		return baseReportsPath;
	}
}
