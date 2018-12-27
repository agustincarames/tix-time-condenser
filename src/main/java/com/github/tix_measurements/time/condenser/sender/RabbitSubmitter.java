package com.github.tix_measurements.time.condenser.sender;

import java.io.IOException;
import java.util.Optional;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.CorrelationData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.tix_measurements.time.condenser.utils.jackson.TixPacketSerDe;

@Component
public class RabbitSubmitter {
    private final RabbitTemplate rabbitTemplate;
    private final String outQueueName;
	private final TixPacketSerDe serde;
	
    public RabbitSubmitter(RabbitTemplate rabbitTemplate, 
                           @Value("${tix-condenser.queues.sending.name}") String outQueueName) {
    	this.rabbitTemplate = rabbitTemplate;
    	this.outQueueName = outQueueName;
		this.serde = new TixPacketSerDe();
    }
    
    public void send(SubmittablePacketSet data) throws IOException {
    	Message message = new Message(serde.serializeList(data.packetsToSubmit()), new MessageProperties());
    	rabbitTemplate.convertAndSend("", outQueueName, message, new CorrelationData(data.getId()));
    	
		Optional<SubmittablePacketSet> next = data.onSubmitSuccess();
		if (next.isPresent()) {
			this.send(next.get());
		}
    }
}
