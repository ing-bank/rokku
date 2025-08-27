#!/bin/bash
#it start server with multi namespaces (e.g. supported by ECS implementation) enabled and the NAMESPACE_S3_CREDENTIALS_2 are correct for dev env
export ROKKU_NAMESPACES_ENABLED=true
export NAMESPACE_S3_CREDENTIALS_1=aa,bb
export NAMESPACE_S3_CREDENTIALS_2=accesskey,secretkey
sbt run
