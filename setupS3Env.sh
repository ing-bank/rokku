#!/bin/bash


docker cp src/main/resources/default-bucket-policy.json $(docker-compose ps -q ceph):/root
docker-compose exec ceph radosgw-admin user modify --uid ceph-admin --system
docker-compose exec ceph s3cmd setpolicy /root/default-bucket-policy.json s3://demobucket
docker-compose exec ceph s3cmd put /etc/issue s3://demobucket/subdir/
docker-compose exec ceph s3cmd mb s3://home
docker-compose exec ceph s3cmd put /etc/issue s3://home/testuser/
docker-compose exec ceph s3cmd put /etc/issue s3://home/testuser1/
