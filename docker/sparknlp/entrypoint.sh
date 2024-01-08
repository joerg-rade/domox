#!/bin/bash

# Load the license from sparknlp_keys.json and export the values as OS variables
export_json () {
for s in $(echo $values | jq -r 'to_entries|map("\(.key)=\(.value|tostring)")|.[]' $1 ); do
    export $s
done
}

export_json "/content/sparknlp_keys.json"

# Installs the proper version of Spark NLP for Healthcare
pip install --upgrade spark-nlp-jsl==$JSL_VERSION --user --extra-index-url [https://pypi.johnsnowlabs.com/$SECRET](https://pypi.johnsnowlabs.com/$SECRET)

if [ $? != 0 ];
then
exit 1
fi

# Script to create FastAPI endpoints and preloading pipelines for inference
python3 /content/main.py