FROM python:3.12-alpine@sha256:e6dae589c1c05976188fd3bd019afd091445fc533ed2ea29001beddc7368a9c8
ARG SOURCE_REVISION=unknown
LABEL org.opencontainers.image.revision=$SOURCE_REVISION
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY vendor/ /app/
COPY server.py /app/server.py
RUN chmod -R a+rX /app
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=10s --start-period=20s --retries=3 CMD python -c "import socket; s=socket.create_connection(('127.0.0.1',8080),2); s.sendall(b'GET /health HTTP/1.0\r\n\r\n'); assert b' 200 ' in s.recv(64)"
CMD ["python", "server.py"]
