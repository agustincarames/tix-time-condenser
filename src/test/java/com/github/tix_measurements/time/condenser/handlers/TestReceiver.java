package com.github.tix_measurements.time.condenser.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tix_measurements.time.condenser.model.TixInstallation;
import com.github.tix_measurements.time.condenser.model.TixUser;
import com.github.tix_measurements.time.condenser.utils.jackson.TixPacketSerDe;
import com.github.tix_measurements.time.core.data.TixDataPacket;
import com.github.tix_measurements.time.core.util.TixCoreUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.Comparator;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class TestReceiver {
	private static final Path REPORTS_PATH = Paths.get(System.getProperty("java.io.tmpdir"));
	private static final boolean USE_HTTPS = false;
	private static final String API_HOST = "localhost";
	private static final int API_PORT = 80;
	private static final long USER_ID = 1L;
	private static final String USERNAME = "test-user";
	private static final long INSTALLATION_ID = 1L;
	private static final String INSTALLATION_NAME = "test-installation";
	private static final KeyPair INSTALLATION_KEY_PAIR = TixCoreUtils.NEW_KEY_PAIR.get();
	private static final TixPacketSerDe serDe = new TixPacketSerDe();

	private MockRestServiceServer server;
	private TixReceiver receiver;
	private byte[] message;

	private static byte[] generateMessage() throws InterruptedException {
		int reports = 10;
		int timestamps = 4;
		int timestampSize = Long.BYTES;
		int rowSize = timestamps * timestampSize;
		byte[] message = new byte[reports * rowSize];
		for (int i = 0; i < reports; i++) {
			for (int j = 0; j < timestamps; j++) {
				byte[] nanosInBytes = ByteBuffer.allocate(timestampSize).putLong(TixCoreUtils.NANOS_OF_DAY.get()).array();
				for (int k = 0; k < timestampSize; k++) {
					message[i * rowSize + j * timestampSize + k] = nanosInBytes[k];
				}
				Thread.sleep(5L);
			}
		}
		return message;
	}

	@Before
	public void setup() throws InterruptedException {
		receiver = new TixReceiver(REPORTS_PATH.toString(), USE_HTTPS, API_HOST, API_PORT);
		server = MockRestServiceServer.createServer(receiver.getApiClient());
		message = generateMessage();
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
	public void testValidPacket() throws IOException, InterruptedException {
		TixDataPacket packet = new TixDataPacket(
				new InetSocketAddress(InetAddress.getByName("8.8.8.8"), 4500),
				new InetSocketAddress(InetAddress.getLocalHost(), 4500),
				TixCoreUtils.NANOS_OF_DAY.get(),
				USER_ID,
				INSTALLATION_ID,
				INSTALLATION_KEY_PAIR.getPublic().getEncoded(),
				message,
				TixCoreUtils.sign(message, INSTALLATION_KEY_PAIR));
		Thread.sleep(5L);
		packet.setReceptionTimestamp(TixCoreUtils.NANOS_OF_DAY.get());
		ObjectMapper mapper = new ObjectMapper();
		server.expect(requestTo(format("http://%s:%d/api/user/%d", API_HOST, API_PORT, USER_ID)))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(mapper.writeValueAsString(new TixUser(USER_ID, USERNAME, true)), MediaType.APPLICATION_JSON));
		server.expect(requestTo(format("http://%s:%d/api/user/%d/installation/%d", API_HOST, API_PORT, USER_ID, INSTALLATION_ID)))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(
						mapper.writeValueAsString(new TixInstallation(INSTALLATION_ID, INSTALLATION_NAME,TixCoreUtils.ENCODER.apply(INSTALLATION_KEY_PAIR.getPublic().getEncoded()))),
						MediaType.APPLICATION_JSON));
		receiver.receiveMessage(serDe.serialize(packet));
		server.verify();
		Path expectedReportPath = REPORTS_PATH.resolve(Long.toString(USER_ID)).resolve(Long.toString(INSTALLATION_ID));
		assertThat(expectedReportPath)
				.exists()
				.isDirectory();
		assertThat(Files.walk(expectedReportPath).count()).isEqualTo(2);
		final TixDataPacket expectedPacket = packet;
		try (Stream<Path> paths = Files.walk(expectedReportPath)) {
			paths.forEach(file -> {
				if (file.equals(expectedReportPath)) {
					return;
				}
				assertThat(file)
						.exists()
						.isRegularFile();
				assertThat(file.getFileName().toString())
						.startsWith(TixReceiver.REPORTS_FILE_SUFFIX)
						.endsWith(TixReceiver.REPORTS_FILE_EXTENSION);
				try (BufferedReader reader = Files.newBufferedReader(file)) {
					assertThat(reader.lines().count()).isEqualTo(1);
					reader.lines().forEach(reportLine -> {
						try {
							TixDataPacket filePacket = serDe.deserialize(reportLine.getBytes());
							assertThat(filePacket).isEqualTo(expectedPacket);
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

	@Test
	public void testInvalidUser() throws InterruptedException, UnknownHostException, JsonProcessingException {
		long otherUserId = USER_ID + 1L;
		TixDataPacket packet = new TixDataPacket(
				new InetSocketAddress(InetAddress.getByName("8.8.8.8"), 4500),
				new InetSocketAddress(InetAddress.getLocalHost(), 4500),
				TixCoreUtils.NANOS_OF_DAY.get(),
				otherUserId,
				INSTALLATION_ID,
				INSTALLATION_KEY_PAIR.getPublic().getEncoded(),
				message,
				TixCoreUtils.sign(message, INSTALLATION_KEY_PAIR));
		Thread.sleep(5L);
		packet.setReceptionTimestamp(TixCoreUtils.NANOS_OF_DAY.get());
		server.expect(requestTo(format("http://%s:%d/api/user/%d", API_HOST, API_PORT, otherUserId)))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));
		receiver.receiveMessage(serDe.serialize(packet));
		server.verify();
		Path expectedReportPath = REPORTS_PATH.resolve(Long.toString(USER_ID)).resolve(Long.toString(INSTALLATION_ID));
		assertThat(expectedReportPath).doesNotExist();
	}

	@Test
	public void testDisabledUser() throws InterruptedException, UnknownHostException, JsonProcessingException {
		long otherUserId = USER_ID + 1L;
		TixDataPacket packet = new TixDataPacket(
				new InetSocketAddress(InetAddress.getByName("8.8.8.8"), 4500),
				new InetSocketAddress(InetAddress.getLocalHost(), 4500),
				TixCoreUtils.NANOS_OF_DAY.get(),
				otherUserId,
				INSTALLATION_ID,
				INSTALLATION_KEY_PAIR.getPublic().getEncoded(),
				message,
				TixCoreUtils.sign(message, INSTALLATION_KEY_PAIR));
		Thread.sleep(5L);
		packet.setReceptionTimestamp(TixCoreUtils.NANOS_OF_DAY.get());
		ObjectMapper mapper = new ObjectMapper();
		server.expect(requestTo(format("http://%s:%d/api/user/%d", API_HOST, API_PORT, otherUserId)))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(mapper.writeValueAsString(new TixUser(USER_ID, USERNAME, false)), MediaType.APPLICATION_JSON));
		receiver.receiveMessage(serDe.serialize(packet));
		server.verify();
		Path expectedReportPath = REPORTS_PATH.resolve(Long.toString(USER_ID)).resolve(Long.toString(INSTALLATION_ID));
		assertThat(expectedReportPath).doesNotExist();
	}

	@Test
	public void testInvalidInstallation() throws InterruptedException, UnknownHostException, JsonProcessingException {
		long otherInstallationId = INSTALLATION_ID + 1L;
		TixDataPacket packet = new TixDataPacket(
				new InetSocketAddress(InetAddress.getByName("8.8.8.8"), 4500),
				new InetSocketAddress(InetAddress.getLocalHost(), 4500),
				TixCoreUtils.NANOS_OF_DAY.get(),
				USER_ID,
				otherInstallationId,
				INSTALLATION_KEY_PAIR.getPublic().getEncoded(),
				message,
				TixCoreUtils.sign(message, INSTALLATION_KEY_PAIR));
		Thread.sleep(5L);
		packet.setReceptionTimestamp(TixCoreUtils.NANOS_OF_DAY.get());
		ObjectMapper mapper = new ObjectMapper();
		server.expect(requestTo(format("http://%s:%d/api/user/%d", API_HOST, API_PORT, USER_ID)))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(mapper.writeValueAsString(new TixUser(USER_ID, USERNAME, true)), MediaType.APPLICATION_JSON));
		server.expect(requestTo(format("http://%s:%d/api/user/%d/installation/%d", API_HOST, API_PORT, USER_ID, otherInstallationId)))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));
		receiver.receiveMessage(serDe.serialize(packet));
		server.verify();
		Path expectedReportPath = REPORTS_PATH.resolve(Long.toString(USER_ID)).resolve(Long.toString(INSTALLATION_ID));
		assertThat(expectedReportPath).doesNotExist();
	}

	@Test
	public void testInstallationPublicKey() throws InterruptedException, UnknownHostException, JsonProcessingException {
		KeyPair otherKeyPair = TixCoreUtils.NEW_KEY_PAIR.get();
		TixDataPacket packet = new TixDataPacket(
				new InetSocketAddress(InetAddress.getByName("8.8.8.8"), 4500),
				new InetSocketAddress(InetAddress.getLocalHost(), 4500),
				TixCoreUtils.NANOS_OF_DAY.get(),
				USER_ID,
				INSTALLATION_ID,
				otherKeyPair.getPublic().getEncoded(),
				message,
				TixCoreUtils.sign(message, otherKeyPair));
		Thread.sleep(5L);
		packet.setReceptionTimestamp(TixCoreUtils.NANOS_OF_DAY.get());
		ObjectMapper mapper = new ObjectMapper();
		server.expect(requestTo(format("http://%s:%d/api/user/%d", API_HOST, API_PORT, USER_ID)))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(mapper.writeValueAsString(new TixUser(USER_ID, USERNAME, true)), MediaType.APPLICATION_JSON));
		server.expect(requestTo(format("http://%s:%d/api/user/%d/installation/%d", API_HOST, API_PORT, USER_ID, INSTALLATION_ID)))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(
						mapper.writeValueAsString(new TixInstallation(INSTALLATION_ID, INSTALLATION_NAME,TixCoreUtils.ENCODER.apply(INSTALLATION_KEY_PAIR.getPublic().getEncoded()))),
						MediaType.APPLICATION_JSON));
		receiver.receiveMessage(serDe.serialize(packet));
		server.verify();
		Path expectedReportPath = REPORTS_PATH.resolve(Long.toString(USER_ID)).resolve(Long.toString(INSTALLATION_ID));
		assertThat(expectedReportPath).doesNotExist();
	}
}
