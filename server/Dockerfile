FROM ghcr.io/graalvm/native-image-community:25@sha256:0d936f32bb8acb5bc60c41b33e05f064d7a6aaf36b726538296c54949bd4a3c0 AS build
ARG MAIN_CLASS
WORKDIR /build
COPY lib lib
RUN native-image --no-fallback -march=compatibility --static-nolibc -J-Xmx3g \
    -cp "$(printf '%s:' lib/*.jar)" -o server "$MAIN_CLASS"
RUN ldd server | tee ldd.txt && ! grep -vE 'linux-vdso|libc\.so|ld-linux' ldd.txt

FROM gcr.io/distroless/base-nossl-debian13:nonroot@sha256:6a2a3346335b94d3be0732472245f42ca3ce3cc1135a9521a2bda4b677f2ef0d
ENV PORT=8080 SITE_DIR=/site TEMPLATES_DIR=/templates AGENT_DIR=/agent
EXPOSE 8080
ENTRYPOINT ["/server"]
COPY --from=build /build/server /server
COPY templates /templates
COPY agent /agent
COPY site /site
