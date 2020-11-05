FROM alpine:latest

USER root
RUN apk --no-cache add curl

RUN curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/master/contrib/install.sh | sh -s -- -b /usr/local/bin