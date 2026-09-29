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

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

springBoot {
    mainClass.set("org.pgmbim.audit.Application")
    buildInfo()
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

// --- Docker image build & push -------------------------------------------------
// Registries: default (dev) vs production. Override the tag/repo with -PdockerImage=...
val devRegistry = "10.99.100.116"
val prodRegistry = "10.99.100.142"
val imagePath = "5000/auditing-service:latest"

fun dockerImageFor(registry: String) =
    (findProperty("dockerImage") as String?) ?: "$registry:$imagePath"

// deployImage: uses the production registry when -Production is passed, else dev.
val deployIsProd = hasProperty("production")
val deployImageName = dockerImageFor(if (deployIsProd) prodRegistry else devRegistry)

val dockerBuild by tasks.registering(Exec::class) {
    group = "docker"
    description = "Builds the Docker image ($deployImageName)."
    dependsOn(tasks.named("bootJar"))
    commandLine("docker", "build", "-t", deployImageName, ".")
}

val dockerPush by tasks.registering(Exec::class) {
    group = "docker"
    description = "Pushes the Docker image ($deployImageName) to the registry."
    dependsOn(dockerBuild)
    commandLine("docker", "push", deployImageName)
}

tasks.register("deployImage") {
    group = "docker"
    description = "bootJar -> docker build -> docker push. Pass -Production for $prodRegistry."
    dependsOn(dockerPush)
}

// Dedicated production task (no argument needed) -> always the production registry.
val prodImageName = dockerImageFor(prodRegistry)

val dockerBuildProd by tasks.registering(Exec::class) {
    group = "docker"
    description = "Builds the production Docker image ($prodImageName)."
    dependsOn(tasks.named("bootJar"))
    commandLine("docker", "build", "-t", prodImageName, ".")
}

val dockerPushProd by tasks.registering(Exec::class) {
    group = "docker"
    description = "Pushes the production Docker image ($prodImageName) to the registry."
    dependsOn(dockerBuildProd)
    commandLine("docker", "push", prodImageName)
}

tasks.register("deployImageProduction") {
    group = "docker"
    description = "bootJar -> docker build -> docker push against the production registry ($prodRegistry)."
    dependsOn(dockerPushProd)
}