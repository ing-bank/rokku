# Proxy mode decision document

* Status: Final draft
* Deciders: Niels Denissen, Adam Rempter, Krzysztof Żmij
* Date: 16 August 2018

## Context and Problem Statement

Proxy mode decision determines and impacts next functionality that will be implemented on both STS 
and Proxy services.

## Considered options

### Option 1: Passthrough (with ACL manager)
![alt text](./img/option_1_Passthrough_(with_ACL_manager).png)

In this solution a separate process will periodically translate Ranger policies to S3 ACLs. This way authorisation and
validation of requests can be done by CEPH itself. Authentication of the short lived token goes through the STS service.

**The Good**
* Request processing at proxy is simpler, just token verification
* Original request is sent unmodified
* Security is done per user on Ceph (in case you would get around the proxy)

**The Bad**
* All users must exist in S3 Backend (Ceph in this case)
* All bucket permissions must be defined in S3 Backend (Ceph in this case)
* It may lead to unwanted access in case of ACL manager failure and consistency issues
* Auditing cannot be done by Ranger easily, we'd have to write our own audit logging
* Any update on Ceph might require change to the ACL manager
* If someone changes ACL in Ceph directly, we can have a sync issue with Ranger

### Option 2: Passthrough (with system users on Ceph)
![alt text](./img/option_2_Passthrough.png)

This solution directly connects to Ranger to authorise requests when they come in. Users have their own 
access/secret keys and these also exist on CEPH (or are created if not present yet). They are present as system users
though that can access all resources. This way we can passthrough any request after checking authentication of the token
with STS and authorisation with Ranger. Validation of the request thus happens in Ceph since we don't touch the request
itself.

**The Good**
* Original request is sent unmodified (no need to resign or validate at proxy)
* Ceph will also contain bucket owners/object creators information
* Listing of buckets works (shows only buckets you are the owner of even if you're a system user)

**The Bad**
* User needs to be created on Ceph
* access/secretkey pair need to be handed to user / created on Ceph and managed in case they're lost (could be done by STS though)
* In case a user gets access to Ceph directly, he can use his own access/secret key to do whatever.

### Option 3: NPA
![alt text](./img/option_3_NPA.png)

In this solution authorisation happens with Ranger and Authentication of the token with STS. The original user request 
is validated based on AWS Signature headers so validation happens inside the gargoyle proxy. 
On success a new request is created and sent by proxy to S3 Backend (thus the request is resigned).In this case user 
request is terminated at Proxy and NPA (non personal account) is used by Proxy to prepare and send all requests to 
backend. 

**The Good**
* Particular users do not need to be maintained on Ceph
* It simplifies STS logic and consistency interactions between services 

**The Bad**
* Additional logic has to be created on Proxy to handle resignature process and user
response (eg. list buckets)
* All Ceph permissions are done with use of one NPA account, although security definitions
are also maintained by STS (and related components)
* Changes in AWS signatures we have to support in proxy

## Decision outcome

**Choose Option 2 - Passthrough (with system users on Ceph)**

- Sync issues of ACL manager might lead to unwanted access which we deem a big problem.
- NPA requires resigning which forces us to support many signature types of s3 that might change in the future.
So this could imply maintenance or unforeseen problems with new signature types.
- We'll have an overview of who created/modified what files in Ceph as well. Basically we'll use Ceph as intended and only
take away the authorisation and authentication part towards Gargoyle.

### User flow

A **new user** arrives

1. New user announces himself to STS with valid keycloak token (getSession)
2. STS creates a access/secretkey pair and a short term token
3. User goes to Proxy with these credentials
4. Proxy validates the credentials and authorises requestion.
5. If needed creates the access/secretkey pair in Ceph
6. Request to CEPH is done

This way a user only has to know his login with keycloak, STS can always return him the same acces/secretkey initially
generated. Everytime the user comes back, he will receive again the same access/secretkey from STS as it's linked to his
keycloak account there.

For **AssumeRole** we can do as follows:

1. User asks for domino role with STS
2. STS checks if this is possible with keycloak or Ranger
3. If so he generates a token for this and returns the users own access/secretkey with the new token
4. When user comes to proxy the proxy will ask what user belongs to these credentials, STS will then return the domino
role as the token was generated for that role.
5. Authorisation happens with domino-role
6. Request to CEPH happens with users own access/secretkey
