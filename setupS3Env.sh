#!/bin/bash

docker-compose exec -T ceph radosgw-admin user modify --uid ceph-admin --system
docker-compose exec -T ceph s3cmd put /etc/issue s3://demobucket/subdir/
docker-compose exec -T ceph s3cmd mb s3://home
docker-compose exec -T ceph s3cmd mb s3://rokku_hc_bucket
docker-compose exec -T ceph s3cmd put /etc/issue s3://home/testuser/
docker-compose exec -T ceph s3cmd put /etc/issue s3://home/testuser1/
docker-compose exec -T ceph s3cmd put /etc/issue s3://home/userone/
docker-compose exec -T ceph s3cmd mb s3://shared

#emulate two ecs namespaces for it tests (RequestHandlerS3WithNamespacesItTest)
docker-compose exec -T ceph radosgw-admin user create --uid=namespaceOne --display-name="namespace one" --access-key=nsAccessKeyOne --secret-key=nsSecretKeyOne --system
export AWS_ACCESS_KEY_ID=nsAccessKeyOne
export AWS_SECRET_ACCESS_KEY=nsSecretKeyOne
aws s3 mb s3://nsOneBucket_1 --endpoint http://s3.localhost:8010
aws s3 mb s3://nsOneBucket_2 --endpoint http://s3.localhost:8010
docker-compose exec -T ceph radosgw-admin user create --uid=namespaceTwo --display-name="namespace two" --access-key=nsAccessKeyTwo --secret-key=nsSecretKeyTwo --system
export AWS_ACCESS_KEY_ID=nsAccessKeyTwo
export AWS_SECRET_ACCESS_KEY=nsSecretKeyTwo
aws s3 mb s3://nsTwoBucket_1 --endpoint http://s3.localhost:8010
aws s3 mb s3://nsTwoBucket_2 --endpoint http://s3.localhost:8010
docker-compose exec -T ceph radosgw-admin user modify --uid namespaceOne --system=false
docker-compose exec -T ceph radosgw-admin user modify --uid namespaceTwo --system=false
