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
        && rm -rf /var/lib/apt/lists/* \
        && update-ca-certificates

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH

ENV POSTGRES_DB=invoicegenie
ENV POSTGRES_USER=invoicegenie
ENV POSTGRES_PASSWORD=invoicegenie

ENV COMMIT_HASH=7aa39262e60dac44999bd3f1a8ca51bc0d0b9a3f
ENV REPO_URL=https://github.com/timus97/InvoiceGenie.git
ENV REPO_NAME=InvoiceGenie

WORKDIR /testbed/${REPO_NAME}

RUN git init && \
    git remote add origin ${REPO_URL} && \
    git fetch --depth 1 origin ${COMMIT_HASH} && \
    git checkout FETCH_HEAD && \
    git remote remove origin


#RUN mvn -pl ar-bootstrap -am package -DskipTests -q


EXPOSE 5432
EXPOSE 8080


CMD ["/bin/bash", "-lc", "service postgresql start; if ! su - postgres -c \"psql -tc \\\"SELECT 1 FROM pg_roles WHERE rolname='${POSTGRES_USER}'\\\"\" | grep -q 1; then su - postgres -c \"psql -c \\\"CREATE ROLE ${POSTGRES_USER} LOGIN PASSWORD '${POSTGRES_PASSWORD}';\\\"\"; fi; if ! su - postgres -c \"psql -tc \\\"SELECT 1 FROM pg_database WHERE datname='${POSTGRES_DB}'\\\"\" | grep -q 1; then su - postgres -c \"psql -c \\\"CREATE DATABASE ${POSTGRES_DB} OWNER ${POSTGRES_USER};\\\"\";]
