#!/bin/bash

- docker-compose exec ceph radosgw-admin user modify --uid ceph-admin --system
- docker-compose exec ceph s3cmd put /etc/issue s3://demobucket/subdir/
- docker-compose exec ceph s3cmd mb s3://home
- docker-compose exec ceph s3cmd put /etc/issue s3://home/testuser/
- docker-compose exec ceph s3cmd put /etc/issue s3://home/testuser1/
