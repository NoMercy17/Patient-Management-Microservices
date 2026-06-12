package com.pm.billingservice.grpc;

import billing.BillingRequest;
import billing.BillingResponse;
import billing.BillingServiceGrpc.BillingServiceImplBase;
import com.google.api.Billing;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
StreamObserver has 3 methods you call:
  responseObserver.onNext(response);     send a value
  responseObserver.onError(throwable);   send an error (terminates stream)
  responseObserver.onCompleted();        signal "done"
 */


// tell spring that this is a grpc service
@GrpcService
public class BillingGrpcService extends BillingServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(
            BillingGrpcService.class);

    @Override
    public void createBillingAccount(BillingRequest billingRequest,
                                     StreamObserver<BillingResponse> responseObserver) {

        // checker to see if our server is accepting requests
        log.info("createBillingAccount request received \n{}", billingRequest.toString());

        // Business logic

        BillingResponse response = BillingResponse.newBuilder()
                .setAccountId("123")
                .setStatus("ACTIVE")
                .build();

        // send response from the service, server to the client
        responseObserver.onNext(response);
        responseObserver.onCompleted();

    }


}