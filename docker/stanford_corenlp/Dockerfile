FROM maven:alpine

MAINTAINER Jörg Rade <joerg.rade60@gmail.com>

ARG STANFORD_CORENLP_VERSION="4.2.0"
ARG MODELS="English"

# net-tools, tcpdump added in order to
RUN apk add --update --no-cache unzip wget net-tools tcpdump

RUN wget http://nlp.stanford.edu/software/stanford-corenlp-${STANFORD_CORENLP_VERSION}.zip && \
    unzip stanford-corenlp-${STANFORD_CORENLP_VERSION}.zip && \
    rm stanford-corenlp-${STANFORD_CORENLP_VERSION}.zip

WORKDIR stanford-corenlp-${STANFORD_CORENLP_VERSION}

RUN case "$MODELS" in *"English"*) \
        wget http://nlp.stanford.edu/software/stanford-corenlp-${STANFORD_CORENLP_VERSION}-models-english.jar && \
        mvn install:install-file -Dfile=stanford-corenlp-${STANFORD_CORENLP_VERSION}-models-english.jar \
        -DgroupId=edu.stanford.nlp -DartifactId=stanford-srparser \
        -Dversion=${STANFORD_CORENLP_VERSION} -Dpackaging=jar \
        ;; esac

RUN export CLASSPATH="`find . -name '*.jar'`"

ENV JAVA_MX 8g
ENV PORT 9000
EXPOSE $PORT

CMD java -cp "*" -mx$JAVA_MX edu.stanford.nlp.pipeline.StanfordCoreNLPServer -preload tokenize,ssplit,pos,lemma,ner,parse,depparse,coref
