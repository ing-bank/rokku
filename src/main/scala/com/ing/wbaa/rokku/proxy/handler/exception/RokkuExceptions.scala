package com.ing.wbaa.rokku.proxy.handler.exception

class RokkuThrottlingException(message: String) extends Exception(message)

class RokkuNamespaceBucketNotFoundException(message: String) extends Exception(message)

class RokkuListingBucketsException(message: String) extends Exception(message)

class RokkuPresignExpiredException(message: String) extends Exception(message)
