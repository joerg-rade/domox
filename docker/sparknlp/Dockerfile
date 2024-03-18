# Download base image (Ubuntu 18.04)
FROM ubuntu:18.04

# Install required dependencies
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    openjdk-8-jdk

# Install Spark NLP and PySpark
RUN pip3 install spark-nlp==3.0.3 pyspark==3.1.2

# Set environment variables
ENV PYSPARK_PYTHON=python3
ENV PYSPARK_DRIVER_PYTHON=python3

# Set the entrypoint command
CMD ["pyspark"]

# docker build --progress tty -t sparknlp .
# docker run sparknlp