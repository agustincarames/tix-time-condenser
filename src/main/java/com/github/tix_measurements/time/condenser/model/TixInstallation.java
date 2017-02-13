package com.github.tix_measurements.time.condenser.model;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class TixInstallation {
	private long id;
	private String name;
	private String publicKey;

	public TixInstallation() { /* Needed by Jackson */ }

	public TixInstallation(long id, String name, String publicKey) {
		try {
			assertThat(id).isPositive();
			assertThat(name).isNotEmpty().isNotNull();
			assertThat(publicKey).isNotEmpty().isNotNull();
		} catch (AssertionError ae) {
			throw new IllegalArgumentException(ae);
		}
		this.id = id;
		this.name = name;
		this.publicKey = publicKey;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPublicKey() {
		return publicKey;
	}

	public void setPublicKey(String publicKey) {
		this.publicKey = publicKey;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;

		if (!(o instanceof TixInstallation)) return false;

		TixInstallation that = (TixInstallation) o;

		return new EqualsBuilder()
				.append(getId(), that.getId())
				.append(getName(), that.getName())
				.append(getPublicKey(), that.getPublicKey())
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(getId())
				.append(getName())
				.append(getPublicKey())
				.toHashCode();
	}
}
