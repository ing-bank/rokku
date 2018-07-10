# gargoyle-s3proxy

Template for s3 proxy based on Akka Http.

gargoyle-s3proxy acts as a security layer between s3 user (eg. application using aws sdk) and s3 backend (eg. ceph RadosGW).
It interacts with
* [Session API](https://github.com/kr7ysztof/auth-api) to get users (or KeyCloak)
* [Ranger](https://ranger.apache.org/) to get bucket policies. On ranger, custom [ranger plugin](https://github.com/bolkedebruin/rangers3plugin) is used to define policies. 
* actual S3 Backend

## usage
To run it:
* Set Environment vars
```
-DPROXY_HOST=proxy_host - name or IP of the proxy server 
-DPROXY_PORT=port       - port to listen on
-DAWS_ACCESS_KEY=       - admin credentials on Ceph RadosGW
-DAWS_SECRET_KEY=       - admin secret on Ceph RadosGW   
-DS3_ENDPOINT=          - RadosGW (or S3 backend) endpoint, eg. http://localhost:8080
```
* sbt run

## Local Testing

To test the proxy locally, we need a running S3 API. For this we use a CEPH docker container with Rados Gateway.
The `docker-compose.yml` defines this, run it using:

    docker-compose up

