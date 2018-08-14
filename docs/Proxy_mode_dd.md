# Proxy mode decision document

* Status: Initial draft
* Deciders: Niels Denissen, Adam Rempter, Krzysztof Żmij, Andrew Snare
* Date: 14 August 2018

## Context and Problem Statement

Proxy mode decision determines and impacts next functionality that will be implemented on both STS 
and Proxy services.

## Considered options

* Option 1 - NPA proxy 

Original user request is validated based on AWS Signature headers, and on success
new request is created and sent by proxy to S3 Backend.
In this case user request is terminated at Proxy and NPA (non personal account) is used by Proxy 
to prepare and send all requests to backend.

User request --> validation --> new NPA request --> S3 Backend

* Option 2 - Pass-through proxy 

Original user request is validated based on AWS Signature headers, and on success 
is forwarded unchanged to S3 Backend

User request --> validation --> S3 Backend 

## Decision outcome

Choose Option 1 - NPA proxy because:
It simplifies interactions between STS and related components, potentially reduction consistency
issues.
Also in both cases AWS signature verification has to be done to validate incoming request, so 
the heavy part of request verification has to be done anyway.
Assumption here is that we are able to handle all resignature cases with NPA user

## Pros and cons 

### Option 1 - NPA proxy

* Good, because particular users do not need to be maintained on Ceph
* Good, because it simplifies STS logic and consistency interactions between services 
* Bad, because additional logic has to be created on Proxy to handle resignature process and user
response (eg. list buckets)
* Bad, because all Ceph permissions are done with use of one NPA account, although security definitions
are also maintained by STS (and related components)

### Option 2 - Pass-through proxy

* Good, because request processing at proxy is simpler, just signature verification (no custom response
based on user policy)
* Good, because original request is sent unmodified
* Good, because security is done per user
* Bad, because all users must exist in S3 Backend (Ceph in this case)
* Bad, because all bucket permissions must be defined in S3 Backend (Ceph in this case)
* Bad, because STS service must maintain consistency between Ranger, KeyCloak and Ceph
* Bad, because it may lead to unwanted access in case of STS failure and consistency issues
