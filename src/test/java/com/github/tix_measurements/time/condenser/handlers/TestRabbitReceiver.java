package com.github.tix_measurements.time.condenser.handlers;

import com.github.tix_measurements.time.condenser.PacketGenerator;
import com.github.tix_measurements.time.condenser.handlers.TixReceiver;
import com.github.tix_measurements.time.condenser.utils.jackson.TixPacketSerDe;
import com.github.tix_measurements.time.core.data.TixDataPacket;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@ActiveProfiles("test")
@SpringBootTest
public class TestRabbitReceiver {
	private static final long USER_ID = 1L;
	private static final long INSTALLATION_ID = 1L;

	@Autowired
	private RabbitAdmin rabbitAdmin;

	@Autowired
	private RabbitTemplate rabbitTemplate;
	
	@SuppressWarnings("unused")
	private RabbitReceiver rabbitReceiver;
	
	private TixReceiver tixReceiver;

	@Value("${tix-condenser.queues.receiving.name}")
	private String receivingQueueName;

	private TixPacketSerDe serDe;

	@Before
	public void setup() throws InterruptedException {
		tixReceiver = mock(TixReceiver.class);
		serDe = new TixPacketSerDe();
		rabbitReceiver = new RabbitReceiver(tixReceiver);
	}

	@After
	public void teardown() throws IOException {
		rabbitAdmin.purgeQueue(receivingQueueName, true);
	}

	@Test
	@DirtiesContext
	public void testRabbitSendsPacket() throws Exception {
		TixDataPacket packet = PacketGenerator.createNewPacket(USER_ID, INSTALLATION_ID);
		
		rabbitTemplate.convertAndSend(receivingQueueName, serDe.serialize(packet));
		Thread.sleep(500L);
		
		verify(tixReceiver, times(1)).receiveMessage(packet);
	}
}
