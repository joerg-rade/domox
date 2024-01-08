FROM ubuntu:18.04
RUN apt-get update && apt-get -y update

RUN apt-get -y update \
    && apt-get install -y wget \
    && apt-get install -y jq \
    && apt-get install -y lsb-release \
    && apt-get install -y openjdk-8-jdk-headless \
    && apt-get install -y build-essential python3-pip \
    && pip3 -q install pip --upgrade \
    && apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* \
            /usr/share/man /usr/share/doc /usr/share/doc-base

ENV PYSPARK_DRIVER_PYTHON=python3
ENV PYSPARK_PYTHON=python3

ENV LC_ALL=C.UTF-8
ENV LANG=C.UTF-8

# We expose the FastAPI default port 8515
EXPOSE 8515

# Install all Python required libraries
COPY requirements.txt /
RUN pip install -r /requirements.txt

# Adds the entrypoint to the FastAPI server
COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh

# In /content folder we will have our main.py and the license files
COPY ./content/ /content/
WORKDIR content/

# We tell Docker to run this file when a container is instantiated
ENTRYPOINT ["/entrypoint.sh"]