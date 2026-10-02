FROM yuzutech/kroki:latest

USER root

# Install build tools, GTS dependencies, and Graphviz build requisites
RUN sed -i 's/Components: main restricted/Components: main restricted universe multiverse/' /etc/apt/sources.list.d/ubuntu.sources \
    && apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    autoconf \
    automake \
    libtool \
    pkg-config \
    bison \
    flex \
    libgts-dev \
    libexpat1-dev \
    libz-dev \
    ca-certificates \
    wget \
    && rm -rf /var/lib/apt/lists/*

# Download, build, and install Graphviz with GTS support explicitly enabled
RUN wget https://gitlab.com/api/v4/projects/4207231/packages/generic/graphviz-releases/10.0.1/graphviz-10.0.1.tar.gz \
    && tar -xzf graphviz-10.0.1.tar.gz \
    && cd graphviz-10.0.1 \
    && ./configure --with-gts \
    && make -j$(nproc) \
    && make install \
    && ldconfig \
    && cd .. \
    && rm -rf graphviz-10.0.1*

USER kroki