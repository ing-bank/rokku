#!/bin/bash

sleep 10

cd $RANGER_HOME
./setup.sh

RESOURCES_PATH="/setup/resources"
RANGER_URL="http://localhost:6080"
USERNAME="admin"
PASSWORD="admin"

/opt/ranger-admin/ews/ranger-admin-services.sh start

set -e
while [ $(curl --user $USERNAME:$PASSWORD -o -I -L -s -w "%{http_code}" "$RANGER_URL/service/public/v2/api/servicedef/1") -ne 200 ]
do
	echo "Waiting for ranger endpoint to be reachable..."
	sleep 2
done


echo "Ranger service is reachable!"

# Create users:
for p in $(find "$RESOURCES_PATH/users/" -name *.json ); do
	echo -e "\n\n- Creating user resource for: $p"
	curl -i -X POST --user $USERNAME:$PASSWORD  "$RANGER_URL/service/xusers/secure/users" -H "Content-Type: application/json" --data  "@$p"
done

#Remove default Hive/HDFS service def
curl -i -X DELETE --user $USERNAME:$PASSWORD  "$RANGER_URL/service/public/v2/api/servicedef/1"
curl -i -X DELETE --user $USERNAME:$PASSWORD  "$RANGER_URL/service/public/v2/api/servicedef/3"

#Create service defs
for p in $(find "$RESOURCES_PATH/servicedef/" -name *.json ); do
	echo -e "\n\n- Creating servicedef resource for: $p"
	curl -i -X POST --user $USERNAME:$PASSWORD  "$RANGER_URL/service/public/v2/api/servicedef" -H "Content-Type: application/json" --data  "@$p"
done

#Create services
for p in $(find "$RESOURCES_PATH/service/" -name *.json ); do
	echo -e "\n\n- Creating service resource for: $p"
	curl -i -X POST --user $USERNAME:$PASSWORD  "$RANGER_URL/service/public/v2/api/service" -H "Content-Type: application/json" --data  "@$p"
done

#Create policy
for p in $(find "$RESOURCES_PATH/policy/" -name *.json ); do
	echo -e "\n\n- Creating policy resource for: $p"
	curl -i -X POST --user $USERNAME:$PASSWORD  "$RANGER_URL/service/public/v2/api/policy" -H "Content-Type: application/json" --data  "@$p"
done


tail -f /dev/null
