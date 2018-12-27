package com.github.tix_measurements.time.condenser.store;

import static java.lang.String.format;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.tix_measurements.time.condenser.utils.jackson.TixPacketSerDe;
import com.github.tix_measurements.time.core.data.TixDataPacket;

public class FilesystemInstallationMeasurements extends AbstractInstallationMeasurements {
	public static final Set<PosixFilePermission> REPORTS_DIRECTORIES_PERMISSIONS = Collections.unmodifiableSet(PosixFilePermissions.fromString("rwxr-x---"));
	
	public static final String REPORTS_FILE_SUFFIX = "tix-report";
	public static final String REPORTS_FILE_EXTENSION = "json";
	
	public static final String REPORTS_FILE_NAME_TEMPLATE = format("%s-%%d.%s", REPORTS_FILE_SUFFIX, REPORTS_FILE_EXTENSION);
	public static final Pattern REPORTS_TIMESTAMP_CAPTURE = Pattern.compile(format("%s-(\\d+).%s", REPORTS_FILE_SUFFIX, REPORTS_FILE_EXTENSION));
	
	private final Path baseReportsPath;
	private final long userId;
	private final long installationId;
	private List<Long> samples;

	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	private final TixPacketSerDe packetSerDe = new TixPacketSerDe();
	
	public static Map<Long, InstallationMeasurements> loadFrom(Path baseReportsPath) throws IOException {
		final Logger logger = LoggerFactory.getLogger(FilesystemInstallationMeasurements.class);
		if (!Files.exists(baseReportsPath)) {
			return new HashMap<>();
		}
			
		logger.info("Loading full reports directory");
		Map<Long, InstallationMeasurements> result = new HashMap<>();
		try (Stream<Path> paths = Files.walk(baseReportsPath)) {
			paths
				.filter(path -> path.getNameCount() == baseReportsPath.getNameCount() + 2)
				.forEach(path -> {
					long userId = Long.parseLong(path.getName(path.getNameCount() - 2).toString());
					long installationId = Long.parseLong(path.getName(path.getNameCount() - 1).toString());
					try {
						result.put(installationId, new FilesystemInstallationMeasurements(baseReportsPath, userId, installationId));
					} catch (IOException e) {
						throw new UncheckedIOException(e); 
					}
				});
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
		return result;
	}
	
	public FilesystemInstallationMeasurements(Path baseReportsPath, Long userId, Long installationId) throws IOException {
		this.baseReportsPath = baseReportsPath;
		this.userId = userId;
		this.installationId = installationId;
		this.samples = new ArrayList<>();
		
		if (Files.exists(reportDirectory())) {
			logger.info("Loading reports directory (user {}, installation {})", userId, installationId);
			try (Stream<Path> paths = Files.walk(reportDirectory())) {
				paths.map(path -> {
						String fileName = path.getFileName().toString();
						return REPORTS_TIMESTAMP_CAPTURE.matcher(fileName);
					})
					.filter(Matcher::matches)
					.mapToLong(matcher -> {
						return Long.parseLong(matcher.group(1));
					})
					.forEach(samples::add);
				samples.sort(Comparator.naturalOrder());
				logger.info("Reports directory (user {}, installation {}) successfully loaded", userId, installationId);
			}
		}
	}
	
	@Override
	public long getInstallationId() {
		return installationId;
	}

	@Override
	public List<Long> sampleStartTimes() {
		return samples;
	}

	@Override
	public void append(TixDataPacket packet) throws IOException {
		Path reportDirectory = reportDirectory();
		logger.debug("reportDirectory={}", reportDirectory);
		if (!Files.exists(reportDirectory)) {
			logger.info("Creating reports directory");
			Files.createDirectories(reportDirectory, PosixFilePermissions.asFileAttribute(REPORTS_DIRECTORIES_PERMISSIONS));
		}

		long firstReportTimestamp = TixPacketSerDe.getFirstReportTimestamp(packet);
		Path reportPath = reportDirectory.resolve(format(REPORTS_FILE_NAME_TEMPLATE, firstReportTimestamp));
		if (!Files.exists(reportPath)) {
			logger.info("Creating report file {}", reportPath);
			Files.createFile(reportPath);
			try (BufferedWriter writer = Files.newBufferedWriter(reportPath)) {
				writer.write(new String(packetSerDe.serialize(packet)));
				samples.add(firstReportTimestamp);
				samples.sort(Comparator.naturalOrder());
			}
			logger.info("Report file {} successfully created", reportPath);
		} else {
			logger.info("Report file {} already exists, not writing to disk.", reportPath);
		}
	}

	@Override
	public List<TixDataPacket> get(List<Long> range) throws IOException {
		ArrayList<TixDataPacket> result = new ArrayList<>(range.size());
		for (long time: range) {
			Path path = reportFile(time);
			TixDataPacket packet = packetSerDe.deserialize(Files.readAllBytes(path));
			result.add(packet);
		}
		return result;
	}

	@Override
	public void delete(List<Long> range) throws IOException {
		for (long time: range) {
			Files.deleteIfExists(reportFile(time));
		}
		samples.removeAll(range);
	}
	
	private Path reportDirectory() {
		return baseReportsPath
				.resolve(Long.toString(userId))
				.resolve(Long.toString(installationId));
	}
	
	private Path reportFile(long time) {
		return reportDirectory()
				.resolve(format(REPORTS_FILE_NAME_TEMPLATE, time));
	}
}
