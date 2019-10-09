#!/bin/bash

token_session=$(curl -s -X POST http://localhost:8080/auth/realms/auth-rokku/protocol/openid-connect/token  -H "Content-Type: application/x-www-form-urlencoded" -d "username=userone" -d "password=password" -d 'grant_type=password' -d 'client_id=sts-rokku' | jq -r '.access_token')
echo "Read keycloak token: $token_session"
if [ ${#token_session} -gt 10 ]
then
  awsjson=$(aws sts get-session-token --endpoint-url http://localhost:12345 --token-code "$token_session")
  awsjson=$(aws sts assume-role --role-arn arn:aws:iam::account-id:role/admin --role-session-name testrole --endpoint-url http://localhost:12345 --token-code "$token_session")
  echo "Aws Json recieved: $awsjson"
  export AWS_ACCESS_KEY_ID=$(echo $awsjson | jq -r '.Credentials.AccessKeyId')
  export AWS_SECRET_ACCESS_KEY=$(echo $awsjson | jq -r '.Credentials.SecretAccessKey')
  export AWS_SESSION_TOKEN=$(echo $awsjson | jq -r '.Credentials.SessionToken')
else
  echo "Invalid user credentials"
fi

