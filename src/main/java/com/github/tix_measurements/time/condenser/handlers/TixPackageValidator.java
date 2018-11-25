package com.github.tix_measurements.time.condenser.handlers;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.github.tix_measurements.time.condenser.model.TixInstallation;
import com.github.tix_measurements.time.condenser.model.TixUser;
import com.github.tix_measurements.time.core.data.TixDataPacket;
import com.github.tix_measurements.time.core.util.TixCoreUtils;
import com.google.common.base.Strings;

@Component
public class TixPackageValidator {
	public static final String USER_TEMPLATE = "%s/user/%d";
	public static final String INSTALLATION_TEMPLATE = "%s/user/%d/installation/%d";

	private final Logger logger = LoggerFactory.getLogger(TixReceiver.class);
	private final HttpHeaders headers;
	private final RestTemplate apiClient;
	private final String apiPath;

	public TixPackageValidator(@Value("${tix-condenser.tix-api.https}") boolean useHttps,
	                           @Value("${tix-condenser.tix-api.host}") String apiHost,
	                           @Value("${tix-condenser.tix-api.port}") int apiPort) {
		logger.info("Creating TixPackageValidator");
		logger.trace("useHttps={} apiHost={} apiPort={}", useHttps, apiHost, apiPort);
		try {
			assertThat(apiHost).isNotEmpty().isNotEmpty();
			assertThat(apiPort).isPositive();
		} catch (AssertionError ae) {
			throw new IllegalArgumentException(ae);
		}
		this.headers = new HttpHeaders();
		this.apiClient = new RestTemplate();
		this.apiPath = format("http%s://%s:%d/api", useHttps ? "s" : "", apiHost, apiPort);
	}

	public boolean validUserAndInstallation(TixDataPacket packet) {
		try {
			return validUser(packet) && validInstallation(packet);
		} catch (HttpClientErrorException hcee) {
			if (hcee.getStatusCode() == HttpStatus.NOT_FOUND) {
				logger.info("Discarding 404 silently");
				return false;
			} else {
				logger.error("Client Error caught", hcee);
				throw hcee;
			}
		}
	}

	private boolean validUser(TixDataPacket packet) {
		HttpEntity<String> request = new HttpEntity<>(this.headers);
		ResponseEntity<TixUser> userResponseEntity = apiClient.exchange(format(USER_TEMPLATE, apiPath, packet.getUserId()), HttpMethod.GET, request, TixUser.class);
		boolean okResponseStatus = userResponseEntity.getStatusCode() == HttpStatus.OK;
		boolean userEnabled = userResponseEntity.getBody().isEnabled();
		if (!okResponseStatus) {
			logger.warn("Response status is not 200 OK");
		}
		if (!userEnabled) {
			logger.warn("User is disabled");
		}
		return  okResponseStatus && userEnabled;
	}

	private boolean validInstallation(TixDataPacket packet) {
		HttpEntity<String> request = new HttpEntity<>(this.headers);
		ResponseEntity<TixInstallation> installationResponseEntity =
				apiClient.exchange(format(INSTALLATION_TEMPLATE, apiPath, packet.getUserId(), packet.getInstallationId()), HttpMethod.GET, request, TixInstallation.class);
		String packetPk = TixCoreUtils.ENCODER.apply(packet.getPublicKey());
		boolean okResponseStatus = installationResponseEntity.getStatusCode() == HttpStatus.OK;
		if (!okResponseStatus) {
			logger.warn("Response status is not 200 OK");
			return false;
		}
		if (installationResponseEntity.getBody() == null) {
			logger.warn("Response body is empty!");
			return false;
		}
		boolean publicKeyMatch = !Strings.isNullOrEmpty(installationResponseEntity.getBody().getPublicKey()) &&
				installationResponseEntity.getBody().getPublicKey().equals(packetPk);
		if (!publicKeyMatch) {
			logger.warn(format("Installation Public Key do not match with packet Public Key.\nInstallation Public Key %s\nPacket Public Key %s",
					installationResponseEntity.getBody().getPublicKey(), packetPk));
			return false;
		}
		return true;
	}

	public RestTemplate getApiClient() {
		return apiClient;
	}

	public String getApiPath() {
		return apiPath;
	}
}
