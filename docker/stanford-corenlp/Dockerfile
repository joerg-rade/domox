# Use an appropriate base image
FROM openjdk:11-jre-slim

# Update the package index and install wget and unzip
RUN apt-get update && \
    apt-get install -y --no-install-recommends wget unzip && \
    rm -rf /var/lib/apt/lists/*

# Set the working directory
WORKDIR /app

RUN apt-get install -y ca-certificates
# Download Stanford CoreNLP
RUN wget --no-check-certificate https://nlp.stanford.edu/software/stanford-corenlp-4.5.8.zip
RUN unzip stanford-corenlp-4.5.8.zip
RUN rm stanford-corenlp-4.5.8.zip

# Set the environment variable for the classpath
ENV CLASSPATH=/app/stanford-corenlp-4.5.8/*

# Expose the port for the CoreNLP server
EXPOSE 9000

# Start the Stanford CoreNLP server
CMD ["java", "-mx4g", "-cp", "$CLASSPATH", "edu.stanford.nlp.pipeline.StanfordCoreNLPServer", "-port", "9000", "-timeout", "15000"]
