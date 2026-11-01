FROM eclipse-temurin:25-jre@sha256:ecdc0adb37bde1a281c4d245a3cd535004b1fc8322d560f5b09463b248c81df0

ARG VCS_REF=unknown
LABEL org.opencontainers.image.revision="${VCS_REF}"

WORKDIR /app
COPY --chown=10001:10001 backend/ujin/build/libs/*.jar /app/app.jar
COPY --chown=10001:10001 infra/backend-health.sh /app/health.sh
USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
