package nl.wbaa.gargoyle.proxy.data

case class S3Request(
                      path: String,
                      owner: String,
                      method: String,
                      accessType: String,
                      username: String,
                      userGroups: Array[String],
                      clientIp: String,
                      remoteAddr: String,
                      fwdAddresses: Array[String]
                    )
