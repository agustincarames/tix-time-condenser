package com.github.tix_measurements.time.condenser.handlers;

import com.github.tix_measurements.time.condenser.PackageGenerator;
import com.github.tix_measurements.time.condenser.store.MeasurementStore;
import com.github.tix_measurements.time.condenser.utils.jackson.TixPacketSerDe;
import com.github.tix_measurements.time.core.data.TixDataPacket;
import com.github.tix_measurements.time.core.util.TixCoreUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.KeyPair;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

public class TestTixReceiver {
	private static final long USER_ID = 1L;
	private static final long INSTALLATION_ID = 1L;
	private static final KeyPair INSTALLATION_KEY_PAIR = TixCoreUtils.NEW_KEY_PAIR.get();
	private static final TixPacketSerDe TIX_PACKET_SER_DE = new TixPacketSerDe();

	private MeasurementStore measurementStore;
	private TixPackageValidator packageValidator;
	private TixReceiver receiver;
	
	@Before
	public void setup() throws InterruptedException {
		packageValidator = mock(TixPackageValidator.class);
		measurementStore = mock(MeasurementStore.class);
		receiver = new TixReceiver(measurementStore, packageValidator);
	}

	@Test
	public void testConstructor() {
		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> new TixReceiver(null, packageValidator));
		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> new TixReceiver(measurementStore, null));
	}

	@Test
	public void testValidPacket() throws IOException, InterruptedException {
		TixDataPacket packet = PackageGenerator.createNewPacket(USER_ID, INSTALLATION_ID, INSTALLATION_KEY_PAIR);
		when(packageValidator.validUserAndInstallation(packet)).thenReturn(true);
		
		receiver.receiveMessage(packet);
		
		verify(packageValidator, times(1)).validUserAndInstallation(packet);
		verify(measurementStore, times(1)).storePacket(packet);
	}

	@Test
	public void testInvalidPacket() throws Exception {
		long otherUserId = USER_ID + 1L;
		TixDataPacket packet = PackageGenerator.createNewPacket(otherUserId, INSTALLATION_ID, INSTALLATION_KEY_PAIR);
		when(packageValidator.validUserAndInstallation(packet)).thenReturn(false);
		
		receiver.receiveMessage(packet);
		
		verify(packageValidator, times(1)).validUserAndInstallation(packet);
		verify(measurementStore, never()).storePacket(packet);
	}
}
