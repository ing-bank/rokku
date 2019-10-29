#!/bin/bash

docker-compose exec ceph radosgw-admin user modify --uid ceph-admin --system
docker-compose exec ceph s3cmd put /etc/issue s3://demobucket/subdir/
docker-compose exec ceph s3cmd mb s3://home
docker-compose exec ceph s3cmd put /etc/issue s3://home/testuser/
docker-compose exec ceph s3cmd put /etc/issue s3://home/testuser1/
docker-compose exec ceph s3cmd put /etc/issue s3://home/userone/
docker-compose exec ceph s3cmd mb s3://shared

# configure ACLs
export AWS_ACCESS_KEY_ID=accesskey
export AWS_SECRET_ACCESS_KEY=secretkey
aws s3api put-bucket-acl --bucket demobucket --grant-read uri=http://acs.amazonaws.com/groups/global/AuthenticatedUsers --grant-write uri=http://acs.amazonaws.com/groups/global/AuthenticatedUsers --endpoint http://s3.localhost:8010
aws s3api put-bucket-acl --bucket home --grant-read uri=http://acs.amazonaws.com/groups/global/AuthenticatedUsers --grant-write uri=http://acs.amazonaws.com/groups/global/AuthenticatedUsers --endpoint http://s3.localhost:8010
aws s3api put-bucket-acl --bucket shared --grant-read uri=http://acs.amazonaws.com/groups/global/AuthenticatedUsers --grant-write uri=http://acs.amazonaws.com/groups/global/AuthenticatedUsers --endpoint http://s3.localhost:8010
aws s3api put-bucket-policy --bucket demobucket --endpoint http://s3.localhost:8010 --policy '{"Statement": [{"Action": ["s3:GetObject"],"Effect": "Allow","Principal": "*","Resource": ["arn:aws:s3:::*"]}],"Version": "2012-10-17"}'
aws s3api put-bucket-policy --bucket home --endpoint http://s3.localhost:8010 --policy '{"Statement": [{"Action": ["s3:GetObject"],"Effect": "Allow","Principal": "*","Resource": ["arn:aws:s3:::*"]}],"Version": "2012-10-17"}'
aws s3api put-bucket-policy --bucket shared --endpoint http://s3.localhost:8010 --policy '{"Statement": [{"Action": ["s3:GetObject"],"Effect": "Allow","Principal": "*","Resource": ["arn:aws:s3:::*"]}],"Version": "2012-10-17"}'
