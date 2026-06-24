package com.pm.stack;

import com.amazonaws.services.route53.model.VPC;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.CfnHealthCheck;

import java.util.stream.Collectors;

public class LocalStack extends Stack {

    private final Vpc vpc;

    public LocalStack(final App scope, final String id, final StackProps props){
        super(scope, id, props);
        this.vpc = createVpc();

        DatabaseInstance authServiceDb = createDatabase("AuthServiceDB", "auth-service-db");
        DatabaseInstance patientServiceDb = createDatabase("PatientServiceDb", "patient-service-db");

        CfnHealthCheck authDbHealthCheck = createDbHealthCheck(authServiceDb, "AuthServiceDBHealthCheck");
        CfnHealthCheck patientDbHealthCheck = createDbHealthCheck(patientServiceDb, "PatientServiceDBHealthCheck");

        CfnCluster mskCluster = createMSKCluster();
    }

    private Vpc createVpc(){
        return Vpc.Builder
                .create(this, "PatientManagementVPC")
                .vpcName("PatientManagementVPC")
                .maxAzs(2) // vpc will be available in 2 different zones throughout the world
                .build();
    }

    private DatabaseInstance createDatabase(String id, String dbName){
        return DatabaseInstance.Builder
                .create(this, id)
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps.builder()
                                .version(PostgresEngineVersion.VER_17_2)
                                .build()))
                .vpc(vpc) // database connected to the vpc
                .instanceType(InstanceType.of
                        (InstanceClass.BURSTABLE2, InstanceSize.MICRO))
                        // CPU, computer power, store, etc... that we want this db to run on
                .allocatedStorage(20) // the amount of storage to this db
                .credentials(Credentials.fromGeneratedSecret("admin_user"))
                .databaseName(dbName)
                .removalPolicy(RemovalPolicy.DESTROY) /// when we remove the stack we remove the db storage(remove the lingering data)
                .build();
    }

    // give the health status of the database
    private CfnHealthCheck createDbHealthCheck(DatabaseInstance db, String id){
        return CfnHealthCheck.Builder.create(this, id)
                .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
                        .type("TCP") // type of the health check
                        .port(Token.asNumber(db.getDbInstanceEndpointPort())) // going to get the port that our db is running on
                        .ipAddress(db.getDbInstanceEndpointAddress())
                        .requestInterval(20) // check at every 20 sec
                        .failureThreshold(3) // try 3 times before reports failure
                        .build())
                .build();
    }

    // managed (by Aws) kafka server
    private CfnCluster createMSKCluster(){
        return CfnCluster.Builder.create(this, "MSKCluster")
                .clusterName("kafka-cluster")
                .kafkaVersion("2.8.0")
                .numberOfBrokerNodes(1)
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                        .instanceType("kafka.m5.xlarge")
                        .clientSubnets(vpc.getPrivateSubnets().stream().map(
                                ISubnet::getSubnetId).collect(Collectors.toList()))
                                .brokerAzDistribution("DEFAULT").build())// how broker get distributed
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
