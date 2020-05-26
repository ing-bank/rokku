/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ranger.audit.provider.kafka;

import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ranger.audit.destination.AuditDestination;
import org.apache.ranger.audit.model.AuditEventBase;
import org.apache.ranger.audit.model.AuthzAuditEvent;
import org.apache.ranger.audit.provider.MiscUtil;


public class KafkaAuditProvider extends AuditDestination {
    private static final Log LOG = LogFactory.getLog(KafkaAuditProvider.class);

    public static final String AUDIT_MAX_QUEUE_SIZE_PROP = "xasecure.audit.kafka.async.max.queue.size";
    public static final String AUDIT_MAX_FLUSH_INTERVAL_PROP = "xasecure.audit.kafka.async.max.flush.interval.ms";
    public static final String AUDIT_KAFKA_BROKER_LIST = "xasecure.audit.kafka.broker_list";
    public static final String AUDIT_KAFKA_SECURITY_PROTOCOL = "xasecure.audit.kafka.security.protocol";
    public static final String AUDIT_KAFKA_TOPIC_NAME = "xasecure.audit.kafka.topic_name";
    public static final String AUDIT_KAFKA_VALUE_SERIALIZER = "xasecure.audit.kafka.value.serializer";
    public static final String AUDIT_KAFKA_KEY_SERIALIZER = "xasecure.audit.kafka.key.serializer";
    public static final String AUDIT_KAFKA_BATCH_SIZE = "xasecure.audit.kafka.batch.size";
    public static final String AUDIT_KAFKA_LINGER_MS = "xasecure.audit.kafka.linger.ms";
    public static final String AUDIT_KAFKA_ACKS = "xasecure.audit.kafka.acks";
    public static final String AUDIT_KAFKA_SSL_TRUSTSTORE_LOCATION = "xasecure.audit.kafka.ssl.truststore.location";
    public static final String AUDIT_KAFKA_SSL_TRUSTSTORE_PASSWORD = "xasecure.audit.kafka.ssl.truststore.password";
    public static final String AUDIT_KAFKA_SSL_KEYSTORE_LOCATION = "xasecure.audit.kafka.ssl.keystore.location";
    public static final String AUDIT_KAFKA_SSL_KEYSTORE_PASSWORD = "xasecure.audit.kafka.ssl.keystore.password";
    public static final String AUDIT_KAFKA_SSL_KEY_PASSWORD = "xasecure.audit.kafka.ssl.key.password";

    boolean initDone = false;

    Producer<String, String> producer = null;
    String topic = null;

    @Override
    public void init(Properties props) {
        LOG.info("init() called");
        super.init(props);

        topic = MiscUtil.getStringProperty(props,
            AUDIT_KAFKA_TOPIC_NAME);
        if (topic == null || topic.isEmpty()) {
            topic = "ranger_audits";
        }

        try {
            if (!initDone) {
                final Map<String, Object> kakfaProps = getKafkaParams(props);

                LOG.info("Connecting to Kafka producer using properties:"
                    + kakfaProps.toString());

                producer  = MiscUtil.executePrivilegedAction(new PrivilegedAction<Producer<String, String>>() {
                    @Override
                    public Producer<String, String> run(){
                        Producer<String, String> producer = new KafkaProducer<String, String>(kakfaProps);
                        return producer;
                    };
                });

                initDone = true;
            }
        } catch (Throwable t) {
            LOG.fatal("Error initializing kafka:", t);
        }
    }

    @Override
    public boolean log(AuditEventBase event) {
        if (event instanceof AuthzAuditEvent) {
            AuthzAuditEvent authzEvent = (AuthzAuditEvent) event;

            if (authzEvent.getAgentHostname() == null) {
                authzEvent.setAgentHostname(MiscUtil.getHostname());
            }

            if (authzEvent.getLogType() == null) {
                authzEvent.setLogType("RangerAudit");
            }

            if (authzEvent.getEventId() == null) {
                authzEvent.setEventId(MiscUtil.generateUniqueId());
            }
        }

        String message = MiscUtil.stringify(event);
        try {

            if (producer != null) {
                // TODO: Add partition key
                final ProducerRecord<String, String> keyedMessage = new ProducerRecord<String, String>(
                    topic, message);

                MiscUtil.executePrivilegedAction(new PrivilegedAction<Void>() {
                    @Override
                    public Void run(){
                        producer.send(keyedMessage);
                        return null;
                    };
                });

            } else {
                LOG.info("AUDIT LOG (Kafka Down):" + message);
            }
        } catch (Throwable t) {
            LOG.error("Error sending message to Kafka topic. topic=" + topic
                + ", message=" + message, t);
            return false;
        }
        return true;
    }

    @Override
    public boolean log(Collection<AuditEventBase> events) {
        for (AuditEventBase event : events) {
            log(event);
        }
        return true;
    }

    @Override
    public boolean logJSON(String event) {
        AuditEventBase eventObj = MiscUtil.fromJson(event,
            AuthzAuditEvent.class);
        return log(eventObj);
    }

    @Override
    public boolean logJSON(Collection<String> events) {
        for (String event : events) {
            logJSON(event);
        }
        return false;
    }

    @Override
    public void start() {
        LOG.info("start() called");
        // TODO Auto-generated method stub

    }

    @Override
    public void stop() {
        LOG.info("stop() called");
        if (producer != null) {
            try {
                MiscUtil.executePrivilegedAction(new PrivilegedAction<Void>() {
                    @Override
                    public Void run() {
                        producer.close();
                        return null;
                    };
                });
            } catch (Throwable t) {
                LOG.error("Error closing Kafka producer");
            }
        }
    }

    @Override
    public void waitToComplete() {
        LOG.info("waitToComplete() called");
    }

    @Override
    public void waitToComplete(long timeout) {
    }

    @Override
    public void flush() {
        LOG.info("flush() called");
        if (producer != null) {
            try {
                MiscUtil.executePrivilegedAction(new PrivilegedAction<Void>() {
                    @Override
                    public Void run() {
                        producer.flush();
                        return null;
                    }
                });
            } catch (Throwable t) {
                LOG.error("Error flush Kafka producer");
            }
        }
    }

    public boolean isAsync() {
        return true;
    }

    private Map<String, Object> getKafkaParams(Properties props) {
        final Map<String, Object> kakfaProps = new HashMap<String,Object>();
        kakfaProps.put("security.protocol",
            getParamValue(props, AUDIT_KAFKA_SECURITY_PROTOCOL,"PLAINTEXT"));
        kakfaProps.put("bootstrap.servers",
            getParamValue(props, AUDIT_KAFKA_BROKER_LIST,"localhost:9092"));
        kakfaProps.put("value.serializer",
            getParamValue(props, AUDIT_KAFKA_VALUE_SERIALIZER,"org.apache.kafka.common.serialization.StringSerializer"));
        kakfaProps.put("key.serializer",
            getParamValue(props, AUDIT_KAFKA_KEY_SERIALIZER,"org.apache.kafka.common.serialization.StringSerializer"));
        kakfaProps.put("acks", getParamValue(props, AUDIT_KAFKA_ACKS, "1"));
        kakfaProps.put("batch.size", getParamValue(props, AUDIT_KAFKA_BATCH_SIZE, "1"));
        kakfaProps.put("linger.ms", getParamValue(props, AUDIT_KAFKA_LINGER_MS, "1"));
        kakfaProps.put("ssl.truststore.location", getParamValue(props, AUDIT_KAFKA_SSL_TRUSTSTORE_LOCATION, null));
        kakfaProps.put("ssl.truststore.password", getParamValue(props, AUDIT_KAFKA_SSL_TRUSTSTORE_PASSWORD, null));
        kakfaProps.put("ssl.keystore.location", getParamValue(props, AUDIT_KAFKA_SSL_KEYSTORE_LOCATION, null));
        kakfaProps.put("ssl.keystore.password", getParamValue(props, AUDIT_KAFKA_SSL_KEYSTORE_PASSWORD, null));
        kakfaProps.put("ssl.key.password", getParamValue(props, AUDIT_KAFKA_SSL_KEY_PASSWORD, null));
        return kakfaProps;
    }

    private String getParamValue(Properties props, String paramName, String defaultValue) {
        String value = MiscUtil.getStringProperty(props, paramName);
        if (value == null || value.isEmpty()) {
            value = defaultValue;
        }
        return value;
    }

}
