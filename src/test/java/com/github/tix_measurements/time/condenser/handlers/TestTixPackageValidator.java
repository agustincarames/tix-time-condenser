package com.github.tix_measurements.time.condenser.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tix_measurements.time.condenser.model.TixInstallation;
import com.github.tix_measurements.time.condenser.model.TixUser;
import com.github.tix_measurements.time.core.data.TixDataPacket;
import com.github.tix_measurements.time.core.data.TixPacketType;
import com.github.tix_measurements.time.core.util.TixCoreUtils;
import org.apache.logging.log4j.util.Strings;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertFalse;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class TestTixPackageValidator {
	private static final boolean USE_HTTPS = false;
	private static final String API_HOST = "localhost";
	private static final int API_PORT = 80;
	private static final long USER_ID = 1L;
	private static final String USERNAME = "test-user";
	private static final long INSTALLATION_ID = 1L;
	private static final String INSTALLATION_NAME = "test-installation";
	private static final KeyPair INSTALLATION_KEY_PAIR = TixCoreUtils.NEW_KEY_PAIR.get();

	private MockRestServiceServer server;
	private TixPackageValidator packageValidator;
	private byte[] message;   
	
	//@Rule
    //public Timeout globalTimeout = Timeout.millis(5000);

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

	@Before
	public void setup() throws InterruptedException {
		packageValidator = new TixPackageValidator(USE_HTTPS, API_HOST, API_PORT);
		server = MockRestServiceServer.createServer(packageValidator.getApiClient());
		message = generateMessage();
	}

	public static TixDataPacket createNewPacket(byte[] message, long userId, long installationId, KeyPair keyPair) throws UnknownHostException, InterruptedException {
		TixDataPacket packet = new TixDataPacket(
				new InetSocketAddress(InetAddress.getLocalHost(), 4500),
				new InetSocketAddress(InetAddress.getByName("8.8.8.8"), 4500),
				TixCoreUtils.NANOS_OF_DAY.get(),
				userId,
				installationId,
				keyPair.getPublic().getEncoded(),
				message,
				TixCoreUtils.sign(message, keyPair));
		Thread.sleep(5L);
		packet.setReceptionTimestamp(TixCoreUtils.NANOS_OF_DAY.get());
		return packet;
	}

	@Test
	public void testConstructor() {
		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> new TixPackageValidator(true, null, API_PORT));
		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> new TixPackageValidator(true, Strings.EMPTY, API_PORT));
		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> new TixPackageValidator(true, API_HOST, 0));
		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> new TixPackageValidator(true, API_HOST, -1));
	}

	@Test
	public void testValidPacket() throws IOException, InterruptedException {
		TixDataPacket packet = createNewPacket(message, USER_ID, INSTALLATION_ID, INSTALLATION_KEY_PAIR);
		ObjectMapper mapper = new ObjectMapper();
		server.expect(requestTo(format("http://%s:%d/api/user/%d", API_HOST, API_PORT, USER_ID)))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(mapper.writeValueAsString(new TixUser(USER_ID, USERNAME, true)), MediaType.APPLICATION_JSON));
		server.expect(requestTo(format("http://%s:%d/api/user/%d/installation/%d", API_HOST, API_PORT, USER_ID, INSTALLATION_ID)))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(
						mapper.writeValueAsString(new TixInstallation(INSTALLATION_ID, INSTALLATION_NAME,TixCoreUtils.ENCODER.apply(INSTALLATION_KEY_PAIR.getPublic().getEncoded()))),
						MediaType.APPLICATION_JSON));
		
		assertThat(packageValidator.validUserAndInstallation(packet));
		server.verify();
	}

	@Test
	public void testInvalidUser() throws InterruptedException, UnknownHostException, JsonProcessingException {
		long otherUserId = USER_ID + 1L;
		TixDataPacket packet = createNewPacket(message, otherUserId, INSTALLATION_ID, INSTALLATION_KEY_PAIR);
		server.expect(requestTo(format("http://%s:%d/api/user/%d", API_HOST, API_PORT, otherUserId)))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));
		
		assertFalse(packageValidator.validUserAndInstallation(packet));
		server.verify();
	}

	@Test
	public void testDisabledUser() throws InterruptedException, UnknownHostException, JsonProcessingException {
		long otherUserId = USER_ID + 1L;
		TixDataPacket packet = createNewPacket(message, otherUserId, INSTALLATION_ID, INSTALLATION_KEY_PAIR);
		ObjectMapper mapper = new ObjectMapper();
		server.expect(requestTo(format("http://%s:%d/api/user/%d", API_HOST, API_PORT, otherUserId)))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(mapper.writeValueAsString(new TixUser(otherUserId, USERNAME, false)), MediaType.APPLICATION_JSON));
		
		assertFalse(packageValidator.validUserAndInstallation(packet));
		server.verify();
	}

	@Test
	public void testInvalidInstallation() throws InterruptedException, UnknownHostException, JsonProcessingException {
		long otherInstallationId = INSTALLATION_ID + 1L;
		TixDataPacket packet = createNewPacket(message, USER_ID, otherInstallationId, INSTALLATION_KEY_PAIR);
		ObjectMapper mapper = new ObjectMapper();
		server.expect(requestTo(format("http://%s:%d/api/user/%d", API_HOST, API_PORT, USER_ID)))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(mapper.writeValueAsString(new TixUser(USER_ID, USERNAME, true)), MediaType.APPLICATION_JSON));
		server.expect(requestTo(format("http://%s:%d/api/user/%d/installation/%d", API_HOST, API_PORT, USER_ID, otherInstallationId)))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));
		
		assertFalse(packageValidator.validUserAndInstallation(packet));
		server.verify();
	}

	@Test
	public void testInstallationPublicKey() throws InterruptedException, UnknownHostException, JsonProcessingException {
		KeyPair otherKeyPair = TixCoreUtils.NEW_KEY_PAIR.get();
		TixDataPacket packet = createNewPacket(message, USER_ID, INSTALLATION_ID, otherKeyPair);
		ObjectMapper mapper = new ObjectMapper();
		server.expect(requestTo(format("http://%s:%d/api/user/%d", API_HOST, API_PORT, USER_ID)))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(mapper.writeValueAsString(new TixUser(USER_ID, USERNAME, true)), MediaType.APPLICATION_JSON));
		server.expect(requestTo(format("http://%s:%d/api/user/%d/installation/%d", API_HOST, API_PORT, USER_ID, INSTALLATION_ID)))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(
						mapper.writeValueAsString(new TixInstallation(INSTALLATION_ID, INSTALLATION_NAME,TixCoreUtils.ENCODER.apply(INSTALLATION_KEY_PAIR.getPublic().getEncoded()))),
						MediaType.APPLICATION_JSON));
		
		assertFalse(packageValidator.validUserAndInstallation(packet));
		server.verify();
	}
}
