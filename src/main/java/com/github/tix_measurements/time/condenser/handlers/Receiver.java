package com.github.tix_measurements.time.condenser.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tix_measurements.time.core.data.TixDataPacket;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.Set;

@Service
public class Receiver {
	private static final Set<PosixFilePermission> REPORTS_DIRECTORIES_PERMISSIONS = Collections.unmodifiableSet(PosixFilePermissions.fromString("rwxr-x---"));

	private final ObjectMapper mapper;
	private final Path reportsPath;

	public Receiver(@Value("${tix-condenser.reports.path}") String reportsPath) {
		this.reportsPath = Paths.get(reportsPath).toAbsolutePath();
		this.mapper = new ObjectMapper();
	}

	@RabbitListener(queues = "${tix-condenser.queues.receiving.name}")
	public void receiveMessage(@Payload byte[] message) {
		try {
			TixDataPacket packet = mapper.readValue(new String(message), TixDataPacket.class);
			Path reportDirectory = reportsPath.resolve(packet.getPublicKey());
			if (!Files.exists(reportDirectory) && Files.notExists(reportDirectory)) {
				Files.createDirectory(reportDirectory,
						PosixFilePermissions.asFileAttribute(REPORTS_DIRECTORIES_PERMISSIONS));
			} else {
				throw new Error("Cannot assert directory existence. Maybe privilege issues?");
			}
			Path reportFile = reportDirectory.resolve(packet.getFilename());
			Files.createFile(reportFile);
			Files.write(reportFile, packet.getMessage().getBytes());
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
	}
}
