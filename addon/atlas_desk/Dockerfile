# ATLAS Desk: stdlib-only Python backend + static PWA. No pip installs.
ARG BUILD_FROM=python:3.12-slim
FROM ${BUILD_FROM}
ENV PYTHONUNBUFFERED=1
WORKDIR /app
COPY backend /app/backend
COPY frontend /app/frontend
COPY run.sh /run.sh
RUN chmod +x /run.sh
EXPOSE 8098
CMD ["/run.sh"]
