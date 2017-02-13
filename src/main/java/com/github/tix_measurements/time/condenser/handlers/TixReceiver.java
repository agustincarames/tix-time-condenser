package com.github.tix_measurements.time.condenser.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tix_measurements.time.condenser.model.TixInstallation;
import com.github.tix_measurements.time.condenser.model.TixUser;
import com.github.tix_measurements.time.condenser.utils.jackson.TixPacketSerDe;
import com.github.tix_measurements.time.core.data.TixDataPacket;
import com.github.tix_measurements.time.core.data.TixPacket;
import com.github.tix_measurements.time.core.util.TixCoreUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Set;

import static java.lang.String.format;

@Service
public class TixReceiver {
	private static final Set<PosixFilePermission> REPORTS_DIRECTORIES_PERMISSIONS = Collections.unmodifiableSet(PosixFilePermissions.fromString("rwxr-x---"));
	public static final String USER_TEMPLATE = "%s/user/%d";
	public static final String INSTALLATION_TEMPLATE = "%s/installation/%d";
	public static final String REPORTS_FILE_SUFFIX = "tix-report";
	public static final String REPORTS_FILE_EXTENSION = "json";
	public static final String REPORTS_FILE_NAME_TEMPLATE = format("%s-%%d.%s", REPORTS_FILE_SUFFIX, REPORTS_FILE_EXTENSION);

	private final TixPacketSerDe packetSerDe;
	private final RestTemplate apiClient;
	private final Path baseReportsPath;
	private final String apiPath;

	public TixReceiver(@Value("${tix-condenser.reports.path}") String reportsPath,
	                   @Value("&{tix-condenser.tix-api.https") boolean useHttps,
	                   @Value("${tix-condenser.tix-api.host") String apiHost,
	                   @Value("${tix-condenser.tix-api.port") int apiPort) {
		this.packetSerDe = new TixPacketSerDe();
		this.apiClient = new RestTemplate();
		this.baseReportsPath = Paths.get(reportsPath).toAbsolutePath();
		this.apiPath = format("http%s://%s:%d/api", useHttps ? "s" : "", apiHost, apiPort);
	}

	private boolean validUser(TixDataPacket packet) {
		ResponseEntity<TixUser> userResponseEntity = apiClient.getForEntity(format(USER_TEMPLATE, apiPath, packet.getUserId()), TixUser.class);
		return userResponseEntity.getStatusCode() == HttpStatus.OK &&
				userResponseEntity.getBody().isEnabled();
	}

	private boolean validInstallation(TixDataPacket packet) {
		String userPath = format(USER_TEMPLATE, apiPath, packet.getUserId());
		ResponseEntity<TixInstallation> installationResponseEntity =
				apiClient.getForEntity(format(INSTALLATION_TEMPLATE, userPath, packet.getInstallationId()), TixInstallation.class);
		String packetPk = TixCoreUtils.ENCODER.apply(packet.getPublicKey());
		return installationResponseEntity.getStatusCode() == HttpStatus.OK &&
				installationResponseEntity.getBody().getPublicKey().equals(packetPk);
	}

	private boolean validUserAndInstallation(TixDataPacket packet) {
		return validUser(packet) && validInstallation(packet);
	}

	@RabbitListener(queues = "${tix-condenser.queues.receiving.name}")
	public void receiveMessage(@Payload byte[] message) {
		try {
			final TixDataPacket packet = packetSerDe.deserialize(message);
			if (packet.isValid()) {
				if (validUserAndInstallation(packet)) {
					Path reportDirectory = baseReportsPath.resolve(Long.toString(packet.getUserId()))
							.resolve(Long.toString(packet.getInstallationId()));
					if (!Files.exists(reportDirectory) && Files.notExists(reportDirectory)) {
						Files.createDirectories(reportDirectory,
								PosixFilePermissions.asFileAttribute(REPORTS_DIRECTORIES_PERMISSIONS));
					} else {
						throw new Error("Cannot assert directory existence. Maybe privilege issues?");
					}
					Path reportPath = Files.createFile(reportDirectory.resolve(format(REPORTS_FILE_NAME_TEMPLATE,
									LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))));
					try (BufferedWriter writer = Files.newBufferedWriter(reportPath)) {
						writer.write(new String(packetSerDe.serialize(packet)));
					}
				}
			}
		} catch (IOException ioe) {
			throw new IllegalArgumentException(ioe);
		} catch (HttpClientErrorException hcee) {
			//TODO: Log this!
		}
	}

	public RestTemplate getApiClient() {
		return apiClient;
	}

	public Path getBaseReportsPath() {
		return baseReportsPath;
	}

	public String getApiPath() {
		return apiPath;
	}
}
