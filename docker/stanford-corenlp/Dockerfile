# Use a base image with Java and Maven installed
FROM repository.int.kn/maven:3.9.9-eclipse-temurin-17-alpine

USER root
# Install Git using the apk package manager
RUN apk update && apk add git && apk add ca-certificates

# Add CA certificates
COPY Zscaler_Root_CA.crt /usr/local/share/ca-certificates/
RUN update-ca-certificates

# Set the working directory
WORKDIR /app

# Configure Git to use HTTP/1.1
RUN git config --global http.version HTTP/1.1
# Clone the Stanford CoreNLP repository
RUN git clone https://github.com/stanfordnlp/CoreNLP.git

RUN curl -o /app/CoreNLP/stanford-corenlp-4.5.9-models.jar -L https://huggingface.co/stanfordnlp/corenlp-english-extra/tree/v4.5.9

# Change into the cloned repository directory
WORKDIR /app/CoreNLP
RUN ls -la /app/CoreNLP

# Build the project using Maven
RUN mvn -f pom-java-17.xml -DskipTests clean install #assembly:single

ENV CLASSPATH=/app/CoreNLP/target/classes

# Expose port for the service
EXPOSE 9000

# Start the Stanford CoreNLP server with the English model
CMD java -mx4g -cp "$CLASSPATH" edu.stanford.nlp.pipeline.StanfordCoreNLPServer -port 9000 -timeout 15000 -fileEncoding UTF-8 -outputFormat json
