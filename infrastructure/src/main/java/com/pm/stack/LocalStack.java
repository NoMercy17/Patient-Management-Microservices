package com.pm.stack;

import com.amazonaws.services.route53.model.VPC;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.Vpc;

public class LocalStack extends Stack {

    private final Vpc vpc;

    public LocalStack(final App scope, final String id, final StackProps props){
        super(scope, id, props);
        this.vpc = createVpc();
    }

    private Vpc createVpc(){
        return Vpc.Builder
                .create(this, "PatientManagementVPC")
                .vpcName("PatientManagementVPC")
                .maxAzs(2) // vpc will be available in 2 different zones throughout the world
                .build();
    }


    public static void main(final String[] args){
        // create a new aws cdk app, and where the output should be
        // whenever stack is created, it creates a cloud formation template that goes into the cdk.out folder
        App app = new App(AppProps.builder().outdir("./cdk.out").build());

        // defining additional prop to apply to our stack
        StackProps props = StackProps.builder()
                .synthesizer(new BootstraplessSynthesizer()) // to convert our code into Cloud Formation Template
                .build();


        new LocalStack(app, "localstack", props);
        app.synth();
        System.out.println("App synthesizing in progress...");
    }
}
