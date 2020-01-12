FROM openfaas/of-watchdog:0.7.2 as watchdog
FROM golang:1.12-alpine3.11 as builder

# Allows you to add additional packages via build-arg
ARG ADDITIONAL_PACKAGE
ARG CGO_ENABLED=0
ARG GO111MODULE="off"
ARG GOPROXY=""

COPY --from=watchdog /fwatchdog /usr/bin/fwatchdog
RUN chmod +x /usr/bin/fwatchdog

RUN apk --no-cache add git

WORKDIR /go/src/handler
COPY function/. .

RUN go get
RUN CGO_ENABLED=${CGO_ENABLED} GOOS=linux \
    go build --ldflags "-s -w" -a -installsuffix cgo -o handler .

FROM alpine:3.11
RUN apk --no-cache add \
    ca-certificates

# Add non root user
RUN addgroup -S app && adduser -S -g app app
RUN mkdir -p /home/app

WORKDIR /home/app

COPY --from=builder /usr/bin/fwatchdog .
COPY --from=builder /go/src/handler/handler .

RUN chown -R app /home/app

USER app

ENV exec_timeout="0"
ENV fprocess="./handler"
ENV mode="http"
ENV upstream_url="http://127.0.0.1:8000"

EXPOSE 8080
HEALTHCHECK --interval=3s CMD [ -e /tmp/.lock ] || exit 1

CMD ["./fwatchdog"]
