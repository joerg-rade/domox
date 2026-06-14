FROM eclipse-temurin:11-jre-alpine

# Set CoreNLP version here
ENV CORENLP_VERSION=4.5.10
ENV CORENLP_HOME=/opt/corenlp

# Install curl + unzip + bash + ca-certificates
RUN apk add --no-cache curl unzip bash ca-certificates && update-ca-certificates

# Download and extract Stanford CoreNLP
RUN mkdir -p ${CORENLP_HOME} && curl -k -L -o /tmp/corenlp.zip "https://nlp.stanford.edu/software/stanford-corenlp-${CORENLP_VERSION}.zip" && unzip /tmp/corenlp.zip -d ${CORENLP_HOME} && rm /tmp/corenlp.zip

WORKDIR ${CORENLP_HOME}/stanford-corenlp-${CORENLP_VERSION}

# Expose CoreNLP server port
EXPOSE 9000

# Default annotators (you can override at runtime)
ENV CORENLP_ANNOTATORS="tokenize,ssplit,pos,lemma,ner,parse,depparse"

# Start the CoreNLP server
CMD ["bash", "-c", "java -mx4g -cp '*' edu.stanford.nlp.pipeline.StanfordCoreNLPServer \
    -port 9000 \
    -timeout 15000 \
    -annotators ${CORENLP_ANNOTATORS} \
    -preload ${CORENLP_ANNOTATORS} \
    -threads 4"]
