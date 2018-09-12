# How ranger authorizer connects to kerberized ranger to download policies

## Background 

Ranger authorizer connects on startup (and periodically) to Ranger to download policies.
Ranger may require basic (no auth), 2-way SSL or Kerberos to authenticate. Below steps describe 
how to configure authorizer to download policies on cluster with kerberos enabled.    


## Ranger authorizer settings

Ranger authorizer uses `core-sites.xml` (if present) to determine security mode. It should be available in 
HDP cluster or in classpath.

1. check following sections and set accordingly in core-sites.xml

```
<property>
<name>hadoop.security.authentication</name>
<value>kerberos</value>
</property>

<property>
<name>hadoop.security.authorization</name>
<value>true</value>
</property>
```  

to verify if authorizer is connecting to secured endpoints check access_log. it should contain similar attempts

```
127.0.0.1 - - [11/Sep/2018:16:34:51 +0000] "GET /service/plugins/secure/policies/download/testservice?lastKnownVersion=-1&lastActivationTime=0&pluginId=testservice@linux1.tech.pl-testservice&clusterName= HTTP/1.1" 401 - "-" "Java/1.8.0_181
``` 

non secured one connects to `/service/plugins/policies/download/testservice` 

2. Before running service request kerberos ticket

kinit -kt /path/to/user.keytab prinipal/<fqdn host@REALM

3. In ranger plugin settings add following to control which user can download policies


## troubleshooting

If all steps are correctly configured plugin will be listed in Ranger Audit plugins tabs. Otherwise check logs
`ranger-1.1.0-admin/ews/logs/`. For HDP cluster logs are usually in `/var/log/hadoop/ranger/admin`

* catalina.out
* access_log
* ranger-admin-hostname-ranger.log


```
policy.download.auth.users  <username>
```

4. Run proxy service

## test Ranger setup
useful links

[Ranger kerberos setup CWIKI](https://cwiki.apache.org/confluence/display/RANGER/Ranger+installation+in+Kerberized++Environment)
[Kerberos setup on centos](https://gist.github.com/ashrithr/4767927948eca70845db)

* test kerberos with curl

```
# kinit -kt /path/to/keytab principal
# curl --negotiate -u : -vk  -X GET http://linux1.tech.pl:6080/service/plugins/secure/policies/download/testservice
```

