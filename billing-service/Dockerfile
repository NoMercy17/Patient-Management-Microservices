FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .

# install the dependencies and cache unless a change
RUN mvn dependency:go-offline -B

COPY src ./src

# create a single package, .jar - file with all in it
RUN mvn clean package

# to run our build code
FROM eclipse-temurin:21-jdk AS runner

WORKDIR /app

# from builder step to runner step
COPY --from=builder ./app/target/billing-service-0.0.1-SNAPSHOT.jar ./app.jar

EXPOSE 4001
EXPOSE 9001

ENTRYPOINT ["java", "-jar", "app.jar"]