FROM debian:trixie-slim
ENV PORT=8080 SITE_DIR=/site
EXPOSE 8080
USER 65534:65534
ENTRYPOINT ["/server"]
COPY server /server
COPY site /site
