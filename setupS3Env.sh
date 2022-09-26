#!/bin/bash

docker-compose exec -T ceph radosgw-admin user modify --uid ceph-admin --system
docker-compose exec -T ceph s3cmd put /etc/issue s3://demobucket/subdir/
docker-compose exec -T ceph s3cmd mb s3://home
docker-compose exec -T ceph s3cmd mb s3://rokku_hc_bucket
docker-compose exec -T ceph s3cmd put /etc/issue s3://home/testuser/
docker-compose exec -T ceph s3cmd put /etc/issue s3://home/testuser1/
docker-compose exec -T ceph s3cmd put /etc/issue s3://home/userone/
docker-compose exec -T ceph s3cmd mb s3://shared
