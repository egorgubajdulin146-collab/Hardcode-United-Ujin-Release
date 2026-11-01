FROM nginx:stable-alpine@sha256:0e52bd054126cdc040b1276c0af347aeb02a41b9cfe305d6094767ac30e97949
ARG VCS_REF=unknown
LABEL org.opencontainers.image.revision="${VCS_REF}"
COPY frontend/dist/ /usr/share/nginx/html/
COPY infra/nginx.conf /etc/nginx/nginx.conf
# Safe extraction under umask 077 can leave static directories root-only.
RUN find /usr/share/nginx/html -type d -exec chmod 755 {} + \
    && find /usr/share/nginx/html -type f -exec chmod 644 {} +
USER 101:101
ENTRYPOINT ["nginx", "-g", "daemon off;"]
