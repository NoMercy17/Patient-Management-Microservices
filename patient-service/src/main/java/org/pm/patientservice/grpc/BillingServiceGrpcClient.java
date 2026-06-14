package org.pm.patientservice.grpc;

import billing.BillingRequest;
import billing.BillingResponse;
import billing.BillingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


@Service
public class BillingServiceGrpcClient {
    private static final Logger log = LoggerFactory.getLogger(BillingServiceGrpcClient.class);
    // blocking/synchronous client calls to the grpc server
    private final BillingServiceGrpc.BillingServiceBlockingStub blockingStub;


    // in billingService we tested as: -> localhost:9001/BillingService/CreatePatientAccount
    // it will change to smth like: aws.grpc:123412/BillingService/CreatePatientAccount
    public BillingServiceGrpcClient(
            @Value("${billing.service.address:localhost}") String serverAddress,
            @Value("${billing.service.grpc.port:9001}") int serverPort
    ){
        log.info("Connecting to Billing Service GRPC service at {}:{}", serverAddress, serverPort);


        ManagedChannel channel = ManagedChannelBuilder.forAddress(serverAddress, serverPort)
                .usePlaintext().build();

        blockingStub = BillingServiceGrpc.newBlockingStub(channel);
    }

    // from target folder
    public BillingResponse createBillingAccount(String patientId, String name, String email){
        BillingRequest request = BillingRequest.newBuilder().setPatientId(patientId)
                .setName(name).setEmail(email).build();

        // just as we do in the grpc-requests
        BillingResponse response = blockingStub.createBillingAccount(request);
        log.info("Received response from billing service via GRPC: {}", response);
        return response;
    }
}
