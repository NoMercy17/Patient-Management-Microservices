package com.pm.stack;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.CfnHealthCheck;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LocalStack extends Stack {

    private final Vpc vpc;
    private final Cluster ecsCluster;

    public LocalStack(final App scope, final String id, final StackProps props){
        super(scope, id, props);
        this.vpc = createVpc();

        DatabaseInstance authServiceDb = createDatabase("AuthServiceDB", "auth-service-db");
        DatabaseInstance patientServiceDb = createDatabase("PatientServiceDb", "patient-service-db");

        CfnHealthCheck authDbHealthCheck = createDbHealthCheck(authServiceDb, "AuthServiceDBHealthCheck");
        CfnHealthCheck patientDbHealthCheck = createDbHealthCheck(patientServiceDb, "PatientServiceDBHealthCheck");

        CfnCluster mskCluster = createMSKCluster();

        this.ecsCluster = createEcsCluster();

        // Services creation
        FargateService authService = createFargateService("AuthService", "auth-service",
                List.of(4005), authServiceDb, Map.of("JWT_SECRET","7qUQ8QhGeDMj8Fd29EuYuUqpuxduEpFs07nySU2kS0E"));

        // tells the CDK and cloud formation template that authService has a dependency to the db and health check
        authService.getNode().addDependency(authDbHealthCheck);
        authService.getNode().addDependency(authServiceDb);

        FargateService billingService = createFargateService("BillingService", "billing-service",
                List.of(4001, 9001), null, null);

        FargateService analytics = createFargateService("AnalyticsService", "analytics-service",
                List.of(4002), null, null);

        // kafka cluster needs to be running before we start the analytics
        analytics.getNode().addDependency(mskCluster);

        FargateService patientService = createFargateService("PatientService", "patient-service",
                List.of(4000), patientServiceDb, Map.of(
                        "BILLING_SERVICE_ADDRESS", "host.docker.internal",
                        "BILLING_SERVICE_GRPC_PORT", "9001"
                ));

        patientService.getNode().addDependency(patientServiceDb);
        patientService.getNode().addDependency(patientDbHealthCheck);
        patientService.getNode().addDependency(billingService);
        patientService.getNode().addDependency(mskCluster);


        createAPIGatewayService();
    }

    private Vpc createVpc(){
        return Vpc.Builder
                .create(this, "PatientManagementVP")
                .vpcName("PatientManagementVP")
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

    // type of ECS service, easy to start/stop and scale ECS tasks
    private FargateService createFargateService(String id, String imageName,
                                                List<Integer> ports,
                                                DatabaseInstance db,
                                                Map<String, String> additionEnvVars){
        // task definition = blueprint for container
        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder
                .create(this, id + "Task")
                .cpu(256)
                .memoryLimitMiB(512) // MB
                .build();


        ContainerDefinitionOptions.Builder containerOptions = ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry(imageName))
                .portMappings(ports.stream()
                        .map(port -> PortMapping.builder()
                                .containerPort(port)
                                .hostPort(port) // the port that gets exposed for other services to access it
                                .protocol(Protocol.TCP)
                                .build())
                        .toList())
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(LogGroup.Builder.create(this, id + "LogGroup").logGroupName("/ecs" + imageName)
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_DAY)
                                        .build()) // we want to group all the logs for a given container/service into their own group with the same name
                                .streamPrefix(imageName)
                        .build())); // where our logging will go into our container


        Map<String, String> envVars = new HashMap<>();

        envVars.put("SPRING_KAFKA_BOOTSTRAP_SERVERS", "localhost.localstack.cloud:4510, localhost.localstack.cloud:4511, localhost.localstack.cloud:4512");


        if(additionEnvVars != null){
            envVars.putAll(additionEnvVars);
        }

        // connection for the server to connect to the db that has been passed in
        if(db != null){
            envVars.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://%s:%s/%s-db".formatted(
                    db.getDbInstanceEndpointAddress(), db.getDbInstanceEndpointPort(), imageName
            ));

            envVars.put("SPRING_DATASOURCE_USERNAME", "admin_user");
            envVars.put("SPRING_DATASOURCE_PASSWORD", db.getSecret().secretValueFromJson("password").toString());

            envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");
            envVars.put("SPRING_SQL_INIT_MODE", "always");
            envVars.put("SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT", "60000");
        }


        containerOptions.environment(envVars).build();

        // adds a new container to our task definition with those options, that we build it for passing
        taskDefinition.addContainer(imageName + "Container", containerOptions.build());

        return FargateService.Builder.create(this, id)
                .cluster(ecsCluster)
                .taskDefinition(taskDefinition) // will use this task definition to start a container
                .assignPublicIp(false) // closed to the internet
                .serviceName(imageName)
                .build();
    }



    // other microservices can find the auth service by: auth-service.patient-management.local
    private Cluster createEcsCluster(){
        return Cluster.Builder.create(this, "PatientManagementCluster")
                .vpc(vpc)
                // cloud map namespace name patient-management.local for service discovery
                // to allow microservices to find/communicate with each other using this domain
                .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
                        .name("patient-management.local")
                        .build())
                .build();
    }

    // api-gateway + load balancer
    private void createAPIGatewayService(){

        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder
                .create(this, "APIGatewayTaskDefinition")
                .cpu(256)
                .memoryLimitMiB(512) // MB
                .build();

        ContainerDefinitionOptions containerOptions = ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry("api-gateway"))
                .environment(Map.of(
                        "SPRING_PROFILES_ACTIVE", "prod",
                        "AUTH_SERVICE_URL", "http://host.docker.internal:4005"
                ))
                .portMappings(Stream.of(4004)
                        .map(port -> PortMapping.builder()
                                .containerPort(port)
                                .hostPort(port) // the port that gets exposed for other services to access it
                                .protocol(Protocol.TCP)
                                .build())
                        .toList())
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                        .logGroup(LogGroup.Builder.create(this, "APIGatewayLogGroup").logGroupName("/ecs/api-gateway" )
                                .removalPolicy(RemovalPolicy.DESTROY)
                                .retention(RetentionDays.ONE_DAY)
                                .build()) // we want to group all the logs for a given container/service into their own group with the same name
                        .streamPrefix("api-gateway")
                        .build()))
                .build();// where our logging will go into our container

        taskDefinition.addContainer("APIGatewayContainer", containerOptions);

        var apiGateway = ApplicationLoadBalancedFargateService.Builder.create(this, "APIGatewayService")
                .cluster(ecsCluster)
                .serviceName("api-gateway")
                .taskDefinition(taskDefinition)
                .desiredCount(1)
                .healthCheckGracePeriod(Duration.seconds(60))
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
