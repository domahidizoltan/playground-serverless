FROM quay.io/quarkus/centos-quarkus-maven:19.2.1 AS build-aot
RUN mkdir app
WORKDIR /app
COPY function/src src

# the base image has an installed Maven, but here I'm using Gradle wrapper
COPY function/gradle gradle
COPY function/build.gradle build.gradle
COPY function/settings.gradle settings.gradle
COPY function/gradle.properties gradle.properties
COPY function/gradlew gradlew
COPY function/gradlew.bat gradlew.bat

USER root
RUN chown -R quarkus .
USER quarkus
RUN ./gradlew buildNative


FROM openfaas/of-watchdog:0.7.2 as watchdog
FROM frolvlad/alpine-glibc:alpine-3.10

RUN apk add --no-cache ca-certificates
RUN addgroup -S app && adduser -S -g app app

WORKDIR /root/
RUN mkdir -p /root/home/app

WORKDIR /home/app
COPY --from=build-aot /app/build/*-runner /usr/bin/handler
COPY --from=watchdog /fwatchdog /usr/bin/fwatchdog
USER app

ENV cgi_headers="true"
ENV fprocess="handler"
ENV mode="http"
ENV upstream_url="http://127.0.0.1:8000"

ENV exec_timeout="20s"
ENV write_timeout="25s"
ENV read_timeout="25s"

EXPOSE 8080
HEALTHCHECK --interval=1s CMD [ -e /tmp/.lock ] || exit 1

CMD ["fwatchdog"]
