FROM eclipse-temurin:25-jre
WORKDIR /app
COPY build/libs/*.jar /app/
RUN set -eux; \
    jar="$(find /app -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n1)"; \
    if [ -z "$jar" ]; then jar="$(find /app -maxdepth 1 -type f -name '*.jar' | head -n1)"; fi; \
    [ -n "$jar" ]; \
    mv "$jar" /app/app.jar; \
    find /app -maxdepth 1 -type f -name '*.jar' ! -name 'app.jar' -delete
CMD ["java","-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5009","-jar","app.jar"]
