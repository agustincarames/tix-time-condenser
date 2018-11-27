package com.github.tix_measurements.time.condenser.handlers;

import com.github.tix_measurements.time.condenser.PacketGenerator;
import com.github.tix_measurements.time.condenser.store.MeasurementStore;
import com.github.tix_measurements.time.core.data.TixDataPacket;
import com.github.tix_measurements.time.core.util.TixCoreUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.KeyPair;

import static org.mockito.Mockito.*;

public class TestTixReceiver {
	private static final long USER_ID = 1L;
	private static final long INSTALLATION_ID = 1L;
	private static final KeyPair INSTALLATION_KEY_PAIR = TixCoreUtils.NEW_KEY_PAIR.get();

	private MeasurementStore measurementStore;
	private TixPacketValidator packetValidator;
	private TixReceiver receiver;
	
	@Before
	public void setup() throws InterruptedException {
		packetValidator = mock(TixPacketValidator.class);
		measurementStore = mock(MeasurementStore.class);
		receiver = new TixReceiver(measurementStore, packetValidator);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructorWithInvalidMeasurementStore() {
		new TixReceiver(null, packetValidator);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructorWithInvalidPacketValidator() {
		new TixReceiver(measurementStore, null);
	}

	@Test
	public void testValidPacket() throws IOException, InterruptedException {
		TixDataPacket packet = PacketGenerator.createNewPacket(USER_ID, INSTALLATION_ID, INSTALLATION_KEY_PAIR);
		when(packetValidator.validUserAndInstallation(packet)).thenReturn(true);
		
		receiver.receiveMessage(packet);
		
		verify(packetValidator, times(1)).validUserAndInstallation(packet);
		verify(measurementStore, times(1)).storePacket(packet);
	}

	@Test
	public void testInvalidPacket() throws Exception {
		long otherUserId = USER_ID + 1L;
		TixDataPacket packet = PacketGenerator.createNewPacket(otherUserId, INSTALLATION_ID, INSTALLATION_KEY_PAIR);
		when(packetValidator.validUserAndInstallation(packet)).thenReturn(false);
		
		receiver.receiveMessage(packet);
		
		verify(packetValidator, times(1)).validUserAndInstallation(packet);
		verify(measurementStore, never()).storePacket(packet);
	}
}
