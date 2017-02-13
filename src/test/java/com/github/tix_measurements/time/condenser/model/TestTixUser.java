package com.github.tix_measurements.time.condenser.model;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

public class TestTixUser {
	private static final long ID = 1L;
	private static final String USERNAME = "user-test";
	private static final boolean ENABLED = true;

	private com.github.tix_measurements.time.condenser.model.TixUser user;

	@Before
	public void setup() {
		user = new TixUser(ID, USERNAME, ENABLED);
	}

	@Test
	public void testConstructor() {
		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> new TixUser(-1L, USERNAME, ENABLED));
		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> new TixUser(ID, null, ENABLED));
		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> new TixUser(ID, StringUtils.EMPTY, ENABLED));
	}

	@Test
	public void testIdGetterAndSetter() {
		assertThat(user.getId()).isEqualTo(ID);
		Random random = new Random();
		long otherId = ID + 1L;
		user.setId(otherId);
		assertThat(user.getId()).isEqualTo(otherId);
	}

	@Test
	public void testUsernameGetterAndSetter() {
		assertThat(user.getUsername()).isEqualTo(USERNAME);
		String otherUsername = RandomStringUtils.randomAlphabetic(USERNAME.length() + 1);
		user.setUsername(otherUsername);
		assertThat(user.getUsername()).isEqualTo(otherUsername);
	}

	@Test
	public void testEnabledGettersAndSetters() {
		assertThat(user.isEnabled()).isEqualTo(ENABLED);
		boolean newEnabled = !ENABLED;
		user.setEnabled(newEnabled);
		assertThat(user.isEnabled()).isEqualTo(newEnabled);
	}

	@Test
	public void testEquals() {
		assertThat(user).isNotEqualTo(null);
		assertThat(user).isNotEqualTo(new Object());
		assertThat(user).isEqualTo(user);
		TixUser otherUser = new TixUser(user.getId(), user.getUsername(), user.isEnabled());
		assertThat(user).isEqualTo(otherUser);
		otherUser.setEnabled(!otherUser.isEnabled());
		assertThat(user).isNotEqualTo(otherUser);
		otherUser = new TixUser(user.getId(), user.getUsername(), user.isEnabled());
		otherUser.setUsername(RandomStringUtils.randomAlphabetic(USERNAME.length() + 1));
		assertThat(user).isNotEqualTo(otherUser);
		otherUser = new TixUser(user.getId(), user.getUsername(), user.isEnabled());
		otherUser.setId(otherUser.getId() + 1);
		assertThat(user).isNotEqualTo(otherUser);
	}

	@Test
	public void testHashCode() {
		TixUser otherUser = new TixUser(user.getId(), user.getUsername(), user.isEnabled());
		assertThat(user.hashCode()).isEqualTo(otherUser.hashCode());
		otherUser.setEnabled(!otherUser.isEnabled());
		assertThat(user.hashCode()).isNotEqualTo(otherUser.hashCode());
		otherUser = new TixUser(user.getId(), user.getUsername(), user.isEnabled());
		otherUser.setUsername(RandomStringUtils.randomAlphabetic(USERNAME.length() + 1));
		assertThat(user.hashCode()).isNotEqualTo(otherUser.hashCode());
		otherUser = new TixUser(user.getId(), user.getUsername(), user.isEnabled());
		otherUser.setId(otherUser.getId() + 1);
		assertThat(user.hashCode()).isNotEqualTo(otherUser.hashCode());
	}
}
