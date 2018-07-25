# Gargoyle Proxy Design considerations
This document extends Gargoyle initial [design notes](https://hackmd.io/2DzV7iHOTwqHlqPSkbJOiQ) 
Intention is to document all design decisions and doubts.

### Proxy deployment 
Gargoyle proxy will be placed on the same nodes that Ceph RadosGW. RGW will listen on localhost, while
proxy process will bind to external host interface, beeing only point of entry for clients.
Although, possible to run proxy as Docker, in such scenario proxy will be standalone process.

### Stateful vs Stateless
Proxy process should be stateless and should not maintain any data. It is "simple" data
mover with request and policy validation.  

### Scalability
In scenario with Gargoyle proxy on RGW nodes, there will be one 2 one relation between proxy
and RGW.
Other options are possible, if we decide to put proxy outside RGW nodes.
External load balancer should direct users to particular proxy.    

* user sessions

for now assumption is that user sessions are not taken over by other proxy processes, in 
case of proxy crash. Client operation should be repeated then. This may be impossible in case of 
operations like multipart upload?

### Circut braker

Custom mechanism should be considered to be implemented to handle situations like:
* s3 backend is not responding or responding to slowly 

### Software stack
* Scala lang 
* Akka Http toolkit 

Http toolkit that allows transparent passing of incoming and outgoing requests.   

###Proxy modes
* transparent (as much as possible) - **preferred** or  
* with request resignature (using NPA credentials)

### Supported clients
* aws s3api cli (not s3cmd)
* aws SDK  

### Supported operations
All operations that can be invoked by supported clients.
v2 and v3? requests should be discarded, as we support only v4, or proxy should translate older requests
to v4?

### NPA proxy 
* resignature of incoming request

Different AWS tools create request signatures using different fields. eg.   
```
SignedHeaders=amz-sdk-invocation-id;amz-sdk-retry;content-type;host;user-agent;x-amz-content-sha256;x-amz-date 
```
**Rules**:
* Proxy needs to be able to create signature using the same fields, to verify user request
* Proxy may use different headers for request signature between proxy and s3 backend 
* Proxy should copy x-amz-content-sha256 from original request

* bucket ownership 

In this case all buckets belong to one s3 (ceph) user. End users (clients) when listing buckets,
will see all the buckets. Operations like put/get should be checked using Ranger policies.   

### Pass-through proxy
* original user request is copied and send to s3 backend

In this case, proxy will validate incoming request and if request is correct, it will be sent to S3
backend.
In case of Ceph:
* user (with aws credentials) must exist on Ceph, otherwise Ceph will deny access
* bucket ACL has to be associated with bucket, otherwise only bucket owner will have access by
default
This requires that STS keeps consistent state between itself, Ranger and Ceph    

### Event logging 
Log agent allowing log shipment to external system like kafka or ELK.
Logs should be also reported to console or file?  
We should decide on particular software library for this. For instance fluentd can be used to
send messages to various sinks.

### Metrics collection
Proxy should expose metrics, allowing to check current activity. For instance Prometheus 
can be used to expose metrics from each proxy process
