#!/bin/bash

docker-compose exec ceph radosgw-admin user modify --uid ceph-admin --system
docker-compose exec ceph s3cmd put /etc/issue s3://demobucket/subdir/
docker-compose exec ceph s3cmd mb s3://home
docker-compose exec ceph s3cmd put /etc/issue s3://home/testuser/
docker-compose exec ceph s3cmd put /etc/issue s3://home/testuser1/
docker-compose exec ceph s3cmd put /etc/issue s3://home/userone/

# configure ACLs
export AWS_ACCESS_KEY_ID=accesskey
export AWS_SECRET_ACCESS_KEY=secretkey
aws s3api put-bucket-acl --bucket demobucket --grant-read uri=http://acs.amazonaws.com/groups/global/AuthenticatedUsers --grant-write uri=http://acs.amazonaws.com/groups/global/AuthenticatedUsers --endpoint http://localhost:8010
aws s3api put-bucket-acl --bucket home --grant-read uri=http://acs.amazonaws.com/groups/global/AuthenticatedUsers --grant-write uri=http://acs.amazonaws.com/groups/global/AuthenticatedUsers --endpoint http://localhost:8010
