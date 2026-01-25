#!/usr/bin/env bash
aws dynamodb delete-table \
    --table-name posts \
    --endpoint-url http://localhost:8000 \
	  --region eu-west-3 \
	  --profile local

aws dynamodb create-table \
    --table-name posts \
    --attribute-definitions \
        AttributeName=subreddit,AttributeType=S \
        AttributeName=id,AttributeType=S \
    --key-schema \
        AttributeName=subreddit,KeyType=HASH \
        AttributeName=id,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST \
    --endpoint-url http://localhost:8000 \
    --region eu-west-3 \
    --profile local