FROM ghcr.io/graalvm/native-image-community:25 AS build
ARG MAIN_CLASS
WORKDIR /build
COPY lib lib
RUN native-image --no-fallback -march=compatibility -J-Xmx3g \
    -cp "$(printf '%s:' lib/*.jar)" -o server "$MAIN_CLASS"

FROM debian:trixie-slim
ENV PORT=8080 SITE_DIR=/site
EXPOSE 8080
USER 65534:65534
ENTRYPOINT ["/server"]
COPY --from=build /build/server /server
COPY site /site
