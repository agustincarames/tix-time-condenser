package com.github.tix_measurements.time.condenser.handlers;

import com.github.tix_measurements.time.condenser.PacketGenerator;
import com.github.tix_measurements.time.condenser.sender.RabbitSubmitter;
import com.github.tix_measurements.time.condenser.store.MeasurementStore;
import com.github.tix_measurements.time.core.data.TixDataPacket;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static org.mockito.Mockito.*;

public class TestTixReceiver {
	private static final long USER_ID = 1L;
	private static final long INSTALLATION_ID = 1L;

	private MeasurementStore measurementStore;
	private TixPacketValidator packetValidator;
	private RabbitSubmitter submitter;
	private TixReceiver receiver;
	
	@Before
	public void setup() throws InterruptedException {
		packetValidator = mock(TixPacketValidator.class);
		measurementStore = mock(MeasurementStore.class);
		submitter = mock(RabbitSubmitter.class);
		receiver = new TixReceiver(measurementStore, packetValidator, submitter);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructorWithOnlyValidSubmitter() {
		new TixReceiver(null, null, submitter);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructorWithOnlyValidPacketValidator() {
		new TixReceiver(null, packetValidator, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructorWithOnlyValidMeasurementStore() {
		new TixReceiver(measurementStore, null, null);
	}

	@Test
	public void testValidPacket() throws IOException {
		TixDataPacket packet = PacketGenerator.createNewPacket(USER_ID, INSTALLATION_ID);
		when(packetValidator.validUserAndInstallation(packet)).thenReturn(true);
		when(measurementStore.storePacket(packet)).thenReturn(Optional.empty());
		
		receiver.receiveMessage(packet);
		
		verify(packetValidator, times(1)).validUserAndInstallation(packet);
		verify(measurementStore, times(1)).storePacket(packet);
	}

	@Test
	public void testInvalidPacket() throws Exception {
		long otherUserId = USER_ID + 1L;
		TixDataPacket packet = PacketGenerator.createNewPacket(otherUserId, INSTALLATION_ID);
		when(packetValidator.validUserAndInstallation(packet)).thenReturn(false);
		
		receiver.receiveMessage(packet);
		
		verify(packetValidator, times(1)).validUserAndInstallation(packet);
		verify(measurementStore, never()).storePacket(packet);
	}
}
