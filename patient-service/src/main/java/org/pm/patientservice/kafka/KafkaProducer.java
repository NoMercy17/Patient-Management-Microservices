package org.pm.patientservice.kafka;

import org.pm.patientservice.model.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import patient.events.PatientEvent;

@Service
public class KafkaProducer {
    private static final Logger log = LoggerFactory.getLogger(KafkaProducer.class);
    // how we defined our message types
    private final KafkaTemplate<String, byte[]> kafkaTemplate; // key-value

    public KafkaProducer(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendEvent(Patient patient){
        // new event class with properties
        PatientEvent event = PatientEvent.newBuilder()
                .setPatientId(patient.getId().toString())
                .setEmail(patient.getEmail())
                .setName(patient.getName())
                .setEventType("PATIENT_CREATED")
                .build();
        try{
            // send to the "patient" topic
            kafkaTemplate.send("patient",event.toByteArray());

        }catch(Exception e){
            log.error("Error sending PatientCreated event: {}", event);
        }
    }

}
