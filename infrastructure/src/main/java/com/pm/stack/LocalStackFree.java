package com.pm.stack;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Free-tier LocalStack variant.
 * Replaces Pro-only services (RDS, MSK/Kafka, ELB v2) with plain FargateService.
 * Databases and Kafka must be running locally via Docker Compose
 */
public class LocalStackFree extends Stack {

    private final Vpc vpc;
    private final Cluster ecsCluster;

    // Ports your local Docker Compose exposes on the host
    private static final String AUTH_DB_URL    = "jdbc:postgresql://host.docker.internal:5001/auth-service-db";
    private static final String PATIENT_DB_URL = "jdbc:postgresql://host.docker.internal:5000/patient-service-db";
    private static final String DB_USERNAME    = "admin_user";
    private static final String DB_PASSWORD    = "password";
    private static final String KAFKA_BROKERS  = "host.docker.internal:9092";

    public LocalStackFree(final App scope, final String id, final StackProps props) {
        super(scope, id, props);

        this.vpc = createVpc();
        this.ecsCluster = createEcsCluster();

        FargateService authService = createFargateService("AuthService", "auth-service",
                List.of(4005),
                Map.of(
                        "JWT_SECRET", "7qUQ8QhGeDMj8Fd29EuYuUqpuxduEpFs07nySU2kS0E",
                        "SPRING_DATASOURCE_URL", AUTH_DB_URL,
                        "SPRING_DATASOURCE_USERNAME", DB_USERNAME,
                        "SPRING_DATASOURCE_PASSWORD", DB_PASSWORD,
                        "SPRING_JPA_HIBERNATE_DDL_AUTO", "update",
                        "SPRING_SQL_INIT_MODE", "always",
                        "SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT", "60000"
                ));

        FargateService billingService = createFargateService("BillingService", "billing-service",
                List.of(4001, 9001), null);

        FargateService analyticsService = createFargateService("AnalyticsService", "analytics-service",
                List.of(4002), null);

        FargateService patientService = createFargateService("PatientService", "patient-service",
                List.of(4000),
                Map.of(
                        "BILLING_SERVICE_ADDRESS", "host.docker.internal",
                        "BILLING_SERVICE_GRPC_PORT", "9001",
                        "SPRING_DATASOURCE_URL", PATIENT_DB_URL,
                        "SPRING_DATASOURCE_USERNAME", DB_USERNAME,
                        "SPRING_DATASOURCE_PASSWORD", DB_PASSWORD,
                        "SPRING_JPA_HIBERNATE_DDL_AUTO", "update",
                        "SPRING_SQL_INIT_MODE", "always",
                        "SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT", "60000"
                ));

        patientService.getNode().addDependency(billingService);

        createFargateService("APIGatewayService", "api-gateway",
                List.of(4004),
                Map.of(
                        "SPRING_PROFILES_ACTIVE", "prod",
                        "AUTH_SERVICE_URL", "http://host.docker.internal:4005"
                ));
    }

    private Vpc createVpc() {
        return Vpc.Builder
                .create(this, "PatientManagementVPC")
                .vpcName("PatientManagementVPC")
                .maxAzs(2)
                .build();
    }

    private Cluster createEcsCluster() {
        return Cluster.Builder.create(this, "PatientManagementCluster")
                .vpc(vpc)
                .build();
    }

    private FargateService createFargateService(String id, String imageName,
                                                List<Integer> ports,
                                                Map<String, String> envVars) {
        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder
                .create(this, id + "Task")
                .cpu(256)
                .memoryLimitMiB(512)
                .build();

        ContainerDefinitionOptions.Builder containerOptions = ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry(imageName))
                .portMappings(ports.stream()
                        .map(port -> PortMapping.builder()
                                .containerPort(port)
                                .hostPort(port)
                                .protocol(Protocol.TCP)
                                .build())
                        .toList())
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                        .logGroup(LogGroup.Builder.create(this, id + "LogGroup")
                                .logGroupName("/ecs/" + imageName)
                                .removalPolicy(RemovalPolicy.DESTROY)
                                .retention(RetentionDays.ONE_DAY)
                                .build())
                        .streamPrefix(imageName)
                        .build()));

        Map<String, String> allEnvVars = new HashMap<>();
        allEnvVars.put("SPRING_KAFKA_BOOTSTRAP_SERVERS", KAFKA_BROKERS);
        if (envVars != null) {
            allEnvVars.putAll(envVars);
        }
        containerOptions.environment(allEnvVars);

        taskDefinition.addContainer(imageName + "Container", containerOptions.build());

        return FargateService.Builder.create(this, id)
                .cluster(ecsCluster)
                .taskDefinition(taskDefinition)
                .assignPublicIp(false)
                .serviceName(imageName)
                .build();
    }

    public static void main(final String[] args) {
        App app = new App(AppProps.builder().outdir("./cdk.out").build());

        StackProps props = StackProps.builder()
                .synthesizer(new BootstraplessSynthesizer())
                .build();

        new LocalStackFree(app, "localstack", props);
        app.synth();
        System.out.println("App synthesizing in progress...");
    }
}
