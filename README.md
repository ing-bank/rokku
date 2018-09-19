[![Build Status](https://travis-ci.org/arempter/gargoyle-s3proxy.svg?branch=master)](https://travis-ci.org/arempter/gargoyle-s3proxy)
[![codecov.io](http://codecov.io/github/arempter/gargoyle-s3proxy/coverage.svg?branch=master)](https://codecov.io/gh/arempter/gargoyle-s3proxy?branch=master)
[![](https://images.microbadger.com/badges/image/arempter/gargoyle-s3proxy:master.svg)](https://microbadger.com/images/arempter/gargoyle-s3proxy:master)
[![](https://images.microbadger.com/badges/version/arempter/gargoyle-s3proxy:master.svg)](https://microbadger.com/images/arempter/gargoyle-s3proxy:master)

# Gargoyle S3Proxy

gargoyle-s3proxy acts as a security layer between s3 user (eg. application using aws sdk) and s3 backend (eg. ceph RadosGW).

## How to run
To test the proxy (both live and integration tests), we need all dependencies to be running. For this we use a `docker-compose.yml` which defines all dependencies, run it using:

    docker-compose up

When all is runnning we can start the proxy:

    sbt run

NOTE: `ranger-s3-security.xml` must exist in resources directory or in directory from classpath

> for windows docker runs on different it so you need to:
> set environmental variables:
> * GARGOYLE_STS_HOST
> * GARGOYLE_STORAGE_S3_HOST
> * GARGOYLE_KEYCLOAK_TOKEN_URL
> * change GARGOYLE_KEYCLOAK_URL in the docker-compose.yml
> * change the ranger.plugin.s3.policy.rest.url in ranger-s3-security.xml

## Proxy as docker image

When proxy is started as docker image, it will look for ranger-s3-security.xml file to find out how to connect to 
Ranger.
Docker image should be started with volume mount, like

```
docker run -d -v /host/dir/with/xmls:/etc/gargoyle -p 8010:8010 gragoyle-s3proxy
``` 

Sample `ranger-s3-security.xml` can be found in Ranger github repository or in this repository in integration tests 
[resources](https://github.com/arempter/gargoyle-s3proxy/tree/master/src/it/resources) 

## Architecture
![alt text](./docs/img/architecture.png)

Dependencies:
* [Keycloak](https://www.keycloak.org/) for MFA authentication of users.
* [STS Service](https://github.com/kr7ysztof/gargoyle-sts) to provide authentication and short term access to resources on S3.
* [Ranger](https://ranger.apache.org/) to manage authorisation to resources on S3.
The Apache Ranger docker images are created from this repo: https://github.com/nielsdenissen/ranger-for-gargoyle.git
* S3 Backend (Current setup contains Ceph image with RadosGW)

A more in-depth discussion of the architecture and interaction of various components can be found here: [What is Gargoyle?](./docs/What_is_gargoyle.md)


## Docker Ceph settings

In order to enable debug logging on Ceph RadosGW:

1. Edit  /etc/ceph/ceph.conf and add following lines, under [global] section
```
debug rgw = 20
debug civetweb = 20
```

2. Restart rgw process (either docker stop <ceph/daemon rgw> or whole ceph/demo)

## Setting Up AWS CLI

It is possible to set up the AWS command-line tools for working with Ceph RadosGW and Gargoyle. The following instructions assume that you have `virtualenv_wrapper` installed.

1. Create an environment for this work:

       % mkvirtualenv -p python3 gargoyle

2. Install the AWS command-line tools and the endpoint plugin:

       % pip install awscli awscli-plugin-endpoint

3. Configure profiles and credentials for working with Gargoyle or the RadosGW directly:

       % mkdir -p ~/.aws
       % cat >> ~/.aws/credentials << EOF
       [radosgw]
       aws_access_key_id = accesskey
       aws_secret_access_key = secretkey

       [gargoyle]
       aws_access_key_id = accesskey
       aws_secret_access_key = secretkey
       EOF
       % cat >> ~/.aws/config << EOF
       [plugins]
       endpoint = awscli_plugin_endpoint

       [profile gargoyle]
       output = json
       region = localhost
       s3 =
           endpoint_url = http://localhost:8080/
       s3api =
           endpoint_url = http://localhost:8080/
       sts =
           endpoint_url = http://localhost:7080/

       [profile radosgw]
       output = json
       region = localhost
       s3 =
           endpoint_url = http://localhost:8010/
       s3api =
           endpoint_url = http://localhost:8010/
       EOF

4. Configure the default profile and reactivate the virtual environment:

       % cat >> ${WORKON_HOME:-$HOME/.virtualenvs}/gargoyle/bin/postactivate << EOF
       AWS_DEFAULT_PROFILE=gargoyle
       export AWS_DEFAULT_PROFILE
       EOF
       % cat >> ${WORKON_HOME:-$HOME/.virtualenvs}/gargoyle/bin/predeactivate << EOF
       unset AWS_DEFAULT_PROFILE
       EOF
       % deactivate
       % workon gargoyle

By default S3 and STS commands will now be issued against the proxy service. For example:

    % aws s3 ls

Commands can also be issued against the underlying RadosGW service:

    % aws --profile radosgw s3 ls

The default profile can also be switched by modifying the `AWS_DEFAULT_PROFILE` environment variable.
