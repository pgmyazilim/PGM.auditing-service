plugins {
    id("java")
    id("org.springframework.boot") version "4.0.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.protobuf") version "0.9.4"
}

group = "org.pgmbim"
version = "1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
// Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")

    // gRPC
    implementation(platform("io.grpc:grpc-bom:1.80.0"))
    implementation("io.grpc:grpc-services")
    implementation("io.grpc:grpc-netty")
    implementation("io.grpc:grpc-protobuf")
    implementation("io.grpc:grpc-stub")

    // Protobuf
    implementation("com.google.protobuf:protobuf-java:4.33.0")
    implementation("com.google.protobuf:protobuf-java-util:4.33.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.1")
    // Provides JavaTimeModule for DasDataMapper (previously pulled in transitively by DasClientStarter).
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    //Lombok
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
    testCompileOnly("org.projectlombok:lombok:1.18.42")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.42")
}

springBoot {
    mainClass.set("org.pgmbim.audit.Application")
}


protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.33.0"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.76.0"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
            }
        }
    }
}