FROM swe-arena-base

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        openjdk-21-jdk \
        maven \
        sqlite3 \
        libsqlite3-dev \
        postgresql \
        postgresql-client \
        postgresql-contrib \
        ca-certificates \
        curl \
        jq \
        locales \
        libegl1 \
        libgl1 \
        libopengl0 \
        libxcb-xinerama0 \
        libxkbcommon-x11-0 \
        libdbus-1-3 \
        libnss3 \
        libxcomposite1 \
        libxrandr2 \
        libxdamage1 \
        libxfixes3 \
        libasound2 \
        libx11-xcb1 \
        libxcb1 \
        libxcb-glx0 \
        libxcb-keysyms1 \
        libxcb-image0 \
        libxcb-shm0 \
        libxcb-icccm4 \
        libxcb-sync1 \
        libxcb-xfixes0 \
        libxcb-shape0 \
        libxcb-render-util0 \
        x11-utils \
        && rm -rf /var/lib/apt/lists/* \
        && update-ca-certificates

# ✅ Qt fix (prevents shared memory issues in Docker)
ENV QT_X11_NO_MITSHM=1

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH

ENV POSTGRES_DB=invoicegenie
ENV POSTGRES_USER=invoicegenie
ENV POSTGRES_PASSWORD=invoicegenie

ENV COMMIT_HASH=39f7da0d6175b51f3728aa4ae437e597eb46cf8c
ENV REPO_URL=https://github.com/timus97/InvoiceGenie.git
ENV REPO_NAME=invoicegenie

WORKDIR /testbed/${REPO_NAME}

RUN git init && \
    git remote add origin ${REPO_URL} && \
    git fetch --depth 1 origin ${COMMIT_HASH} && \
    git checkout FETCH_HEAD && \
    git remote remove origin

EXPOSE 5432
EXPOSE 8080

CMD ["/bin/bash", "-lc", "\
service postgresql start; \
if ! su - postgres -c \"psql -tc \\\"SELECT 1 FROM pg_roles WHERE rolname='${POSTGRES_USER}'\\\"\" | grep -q 1; then \
    su - postgres -c \"psql -c \\\"CREATE ROLE ${POSTGRES_USER} LOGIN PASSWORD '${POSTGRES_PASSWORD}';\\\"\"; \
fi; \
if ! su - postgres -c \"psql -tc \\\"SELECT 1 FROM pg_database WHERE datname='${POSTGRES_DB}'\\\"\" | grep -q 1; then \
    su - postgres -c \"psql -c \\\"CREATE DATABASE ${POSTGRES_DB} OWNER ${POSTGRES_USER};\\\"\"; \
fi; \
exec bash"]
