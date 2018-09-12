# How to configure ranger plugin for 2-way ssl connection

## Background

Ranger Authorizer can communicate with Apache Ranger using SSL 2-way authentication, in order to download policies.
This guide summarizes steps required to configure both Ranger and Authorizer to allow connection to Secured Rest API.
In case of Hortonworks HDP some additional steps are required (or may differ). For instance Ambari configuration
may need to setup ranger.service.https.attrib.clientAuth = want setting.  

Topics covered in this guide:

* Apache Ranger server SSL setup - standalone Ranger. Setup via Ambari is slightly different
* Keystore, Truststore, credential files generation - using self signed certificates  
* Ranger Authorizer setup - to connect to SSL Rest 

## Configure Ranger SSL (some ranger setup steps are skipped)

In order to setup Apache ranger (standalone) to listen on SSL ports 

* edit `install.properties` file and look for `# ------- PolicyManager CONFIG ----------------` section and
modify following entries. `install.properties` is located in source/install Ranger folder 

```
policymgr_http_enabled=false
policymgr_https_keystore_file=/path/to/ranger-admin-keystore.jks
policymgr_https_keystore_keyalias=rangeradmin
policymgr_https_keystore_password=keystore_pass
```

* run ./setup script to apply changes (located in source/install Ranger folder)

* modify `ranger-admin` script and add following to `JAVA_OPTS`

```
-Djavax.net.ssl.trustStore=/path/to/ranger-admin-truststore.jks -Djavax.net.ssl.trustStorePassword=truststore_pass
```

## How to create keystore and truststore for ranger server and client (self signed) 

* Create Server keystore and export certificate

```
keytool -genkey -keyalg RSA -alias rangeradmin -keystore ranger-admin-keystore.jks -storepass securep -validity 360 -keysize 2048
keytool -export -keystore ranger-admin-keystore.jks -alias rangeradmin -file rangeradmin.cer -storepass securep
```

* Create Client keystore and export certificate

```
keytool -genkey -keyalg RSA -alias gargoyles3plugin -keystore ranger-plugin-keystore.jks -storepass securep -validity 360 -keysize 2048
keytool -export -keystore ranger-plugin-keystore.jks -alias gargoyles3plugin -file gargoyles3plugin.cer -storepass securep
```

* Cross import certificates (create truststores)   

```
keytool -import -file gargoyles3plugin.cer -alias gargoyles3plugin -keystore ranger-admin-truststore.jks -storepass securep
keytool -import -file rangeradmin.cer -alias rangeradmin -keystore ranger-plugin-truststore.jks -storepass securep
```

* Credentials file creation - one file containing credentials for both key and truststore

class path should be updated accordingly  

```
java -cp "/opt/ranger-1.1.0-admin/cred/lib/*" org.apache.ranger.credentialapi.buildks create sslKeyStore -value securep -provider jceks://file/path/to/rangeradmin.jceks
java -cp "/opt/ranger-1.1.0-admin/cred/lib/*" org.apache.ranger.credentialapi.buildks create sslTrustStore -value securep -provider jceks://file/path/to/rangeradmin.jceks

```

## Steps to configure plugin

1. Modify `ranger-s3-security.xml` file in order to point Ranger Authorizer to secure Ranger Rest API

```
<property>
    <name>ranger.plugin.s3.policy.rest.url</name>
    <value>https://localhost:6182</value>
```

2. Add following section to `ranger-s3-security.xml` and adjust value to directory containing `ranger-s3-policymgr-ssl.xml`

```
 <property>
    <name>ranger.plugin.s3.policy.rest.ssl.config.file</name>
    <value>/path/to/ranger-s3-policymgr-ssl.xml</value>
    <description>
      Path to the file containing SSL details to contact Ranger Admin
    </description>
  </property>
```

3. Create file `ranger-s3-policymgr-ssl.xml` with following content (should be created on host where service will run).
How to create keystore, truststore and .jceks files is in separate section

```
<configuration xmlns:xi="http://www.w3.org/2001/XInclude">
  <!--  The following properties are used for 2-way SSL client server validation -->
  <property>
    <name>xasecure.policymgr.clientssl.keystore</name>
    <value>/path/to/ranger-plugin-keystore.jks</value>
    <description>
      Java Keystore files
    </description>
  </property>
  <property>
    <name>xasecure.policymgr.clientssl.keystore.credential.file</name>
    <value>jceks://file/path/to/rangeradmin.jceks</value>
  </property>
  <property>
    <name>xasecure.policymgr.clientssl.truststore</name>
    <value>/path/to/ranger-plugin-truststore.jks</value>
    <description>
      java truststore file
    </description>
  </property>
  <property>
    <name>xasecure.policymgr.clientssl.truststore.credential.file</name>
    <value>jceks://file/path/to/rangeradmin.jceks</value>
  </property>
  <property>
    <name>xasecure.policymgr.clientssl.keystore.password</name>
    <value>keystore_pass</value>
    <description>
      java  keystore credentials
    </description>
  </property>
  <property>
    <name>xasecure.policymgr.clientssl.truststore.password</name>
    <value>truststore_pass</value>
    <description>
      java  truststore credentials
    </description>
  </property>
</configuration>
```

4. Setup certificate common name

Finally add following configuration in Ranger plugin (service) section.

* edit service and in section `Config Properties :`
* in `Add New Configurations` add `commonNameForCertificate` key with value matching client certificate CN. This step
differs through ambari



 

