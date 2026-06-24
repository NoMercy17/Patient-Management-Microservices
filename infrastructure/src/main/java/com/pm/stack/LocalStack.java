package com.pm.stack;

import software.amazon.awscdk.*;

public class LocalStack extends Stack {

    // nothing in here yet
    public LocalStack(final App scope, final String id, final StackProps props){
        super(scope, id, props);
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
