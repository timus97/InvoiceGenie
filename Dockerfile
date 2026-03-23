FROM swe-arena-base

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        openjdk-21-jdk \
        maven \
        sqlite3 \
        libsqlite3-dev \
        ca-certificates \
        curl \
        jq \
        locales \
        && rm -rf /var/lib/apt/lists/* \
        && update-ca-certificates

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH

ENV COMMIT_HASH=main
ENV REPO_URL=https://github.com/timus97/InvoiceGenie.git
ENV REPO_NAME=InvoiceGenie

WORKDIR /testbed/${REPO_NAME}

RUN git init && \
    git remote add origin ${REPO_URL} && \
    git fetch --depth 1 origin ${COMMIT_HASH} && \
    git checkout FETCH_HEAD && \
    git remote remove origin

# Build the project
RUN mvn -pl ar-bootstrap -am package -DskipTests -q

# Expose port
EXPOSE 8080

# Default: run with default profile (PostgreSQL)
CMD ["java", "-jar", "ar-bootstrap/target/quarkus-app/quarkus-run.jar"]
