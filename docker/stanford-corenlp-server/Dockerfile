# referecnes to *.int.kn can be removed, when running "in the wild"
# Use an official OpenJDK image as the base image
FROM repository.int.kn/openjdk:8-jdk-alpine

# Set proxy first (before any network operations)
ENV http_proxy="http://zscaler.proxy.int.kn:80" \
    https_proxy="http://zscaler.proxy.int.kn:80" \
    no_proxy="localhost,127.0.0.1,.int.kn,10.,10.0.0.0/8,.eks.amazonaws.com"

# Set environment variables
ENV CORENLP_VERSION=4.5.9
ENV CORENLP_DIR=/opt/corenlp
ENV CORENLP_URL=https://nlp.stanford.edu/software/stanford-corenlp-${CORENLP_VERSION}.zip

# Create directory for CoreNLP
RUN mkdir -p $CORENLP_DIR

# Download and extract CoreNLP
RUN apk add --no-cache curl unzip && \
    curl -o /tmp/corenlp.zip -L $CORENLP_URL && \
    unzip /tmp/corenlp.zip -d $CORENLP_DIR && \
    rm /tmp/corenlp.zip

# Set up classpath environment variable
ENV CLASSPATH=$CORENLP_DIR/stanford-corenlp-${CORENLP_VERSION}/*
ENV CLASSPATH=$CLASSPATH:$CORENLP_DIR/stanford-corenlp-${CORENLP_VERSION}/lib/*

# Expose port 9000
EXPOSE 9000

# Set working directory
WORKDIR $CORENLP_DIR

# Default command to start the CoreNLP server
CMD ["java", "-mx3g", "edu.stanford.nlp.pipeline.StanfordCoreNLPServer", "--port", "9000"]
