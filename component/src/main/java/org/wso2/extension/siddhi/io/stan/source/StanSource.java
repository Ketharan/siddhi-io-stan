/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.extension.siddhi.io.stan.source;

import io.nats.streaming.StreamingConnection;
import io.nats.streaming.StreamingConnectionFactory;
import io.nats.streaming.Subscription;
import io.nats.streaming.SubscriptionOptions;
import org.apache.log4j.Logger;
import org.wso2.extension.siddhi.io.stan.source.exception.StanInputAdaptorRuntimeException;
import org.wso2.extension.siddhi.io.stan.util.StanConstants;
import org.wso2.siddhi.annotation.Example;
import org.wso2.siddhi.annotation.Extension;
import org.wso2.siddhi.annotation.Parameter;
import org.wso2.siddhi.annotation.util.DataType;
import org.wso2.siddhi.core.config.SiddhiAppContext;
import org.wso2.siddhi.core.exception.ConnectionUnavailableException;
import org.wso2.siddhi.core.stream.input.source.Source;
import org.wso2.siddhi.core.stream.input.source.SourceEventListener;
import org.wso2.siddhi.core.util.config.ConfigReader;
import org.wso2.siddhi.core.util.transport.OptionHolder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is a sample class-level comment, explaining what the extension class does.
 */
@Extension(
        name = "stan",
        namespace = "source",
        description = "Stan Source allows users to subscribe to a Stan broker and receive messages. It has the "
                + "ability to receive Text based messages.",
        parameters = {
                @Parameter(name = StanConstants.DESTINATION,
                        description = "Subject name which Stan Source should subscribe to",
                        type = DataType.STRING
                ),
                @Parameter(name = StanConstants.BOOTSTRAP_SERVERS,
                        description = "The nats based url of the stan server. Coma seperated url values can be used "
                                + "in case of a cluster used.",
                        type = DataType.STRING
                ),
                @Parameter(name = StanConstants.CLIENT_ID,
                        description = "The identifier of the client subscribing/connecting to the stan broker",
                        type = DataType.STRING,
                        optional = true,
                        defaultValue = "stan_client"
                ),
                @Parameter(name = StanConstants.CLUSTER_ID,
                        description = "The identifier of the stan server/cluster.",
                        type = DataType.STRING,
                        optional = true,
                        defaultValue = "test-cluster"
                ),
        },
        examples = {
                @Example(description = "This example shows how to subscribe to a stan subject.",
                        syntax = "@source(type='stan', @map(type='text'), "
                                + "destination='SP_STAN_INPUT_TEST', "
                                + "bootstrap.servers='nats://localhost:4222',"
                                + "client.id='stan_client'"
                                + "server.id='test-cluster"
                                + ")\n" +
                                "define stream inputStream (name string, age int, country string);")
        }
)

public class StanSource extends Source {
    private static final Logger log = Logger.getLogger(StanSource.class);
    private SourceEventListener sourceEventListener;
    private OptionHolder optionHolder;
    private StreamingConnection streamingConnection;
    private String destination;
    private String clusterId;
    private String clientId;
    private String natsUrl;
    private Subscription subscription;
    private SiddhiAppContext siddhiAppContext;
    private StanMessageProcessor stanMessageProcessor;
    private AtomicInteger lastSentSequenceNo = new AtomicInteger(0);
    private String siddhiAppName;
    /**
     * The initialization method for {@link Source}, will be called before other methods. Validates and initiates the
     * stan properties and other required fields.
     * @param sourceEventListener After receiving events, the source should trigger onEvent() of this listener.
     *                            Listener will then pass on the events to the appropriate mappers for processing .
     * @param optionHolder        Option holder containing static configuration related to the {@link Source}
     * @param configReader        ConfigReader is used to read the {@link Source} related system configuration.
     * @param siddhiAppContext    the context of the {@link org.wso2.siddhi.query.api.SiddhiApp} used to get Siddhi
     *                            related utility functions.
     */
    @Override
    public void init(SourceEventListener sourceEventListener, OptionHolder optionHolder,
                     String[] requestedTransportPropertyNames, ConfigReader configReader,
                     SiddhiAppContext siddhiAppContext) {
        this.sourceEventListener = sourceEventListener;
        this.optionHolder = optionHolder;
        this.siddhiAppContext = siddhiAppContext;
        this.stanMessageProcessor = new StanMessageProcessor(sourceEventListener, siddhiAppContext ,
                lastSentSequenceNo);
        this.siddhiAppName = siddhiAppContext.getName();
        initStanProperties();
    }

    /**
     * Returns the list of classes which stan source can output.
     * @return Array of classes that will be output by the source.
     */
    @Override
    public Class[] getOutputEventClasses() {
        return new Class[]{String.class, Map.class};
    }

    /**
     * Initially Called to connect to the stan server for start retrieving the messages asynchronously .
     * @param connectionCallback Callback to pass the ConnectionUnavailableException in case of connection failure after
     *                           initial successful connection. (can be used when events are receiving asynchronously)
     * @throws ConnectionUnavailableException if it cannot connect to the source backend immediately.
     */
    @Override
    public void connect(ConnectionCallback connectionCallback) throws ConnectionUnavailableException {
        try {
            StreamingConnectionFactory streamingConnectionFactory = new StreamingConnectionFactory(this.clusterId,
                    this.clientId);
            streamingConnectionFactory.setNatsUrl(this.natsUrl);
            streamingConnection =  streamingConnectionFactory.createConnection();
        } catch (IOException | InterruptedException e) {
            log.error("Error while connecting to stan server at destination: " + destination);
            throw new ConnectionUnavailableException("Error while connecting to Stan server at destination: "
                    + destination, e);
        }
        subscribe();
    }

    /**
     * This method can be called when it is needed to disconnect from stan server.
     */
    @Override
    public void disconnect() {
        lastSentSequenceNo.set(stanMessageProcessor.getMessageSequenceTracker().get());
        try {
            if (subscription != null) {
                subscription.unsubscribe();
            }
            if (subscription != null) {
                subscription.close();
            }
            if (streamingConnection != null) {
                streamingConnection.close();
            }

        } catch (IOException | TimeoutException | InterruptedException e) {
            log.error("Error disconnecting the Stan receiver", e);
        }
    }

    /**
     * Called at the end to clean all the resources consumed by the {@link Source}.
     */
    @Override
    public void destroy() {

    }

    /**
     * Called to pause event consumption.
     */
    @Override
    public void pause() {

    }

    /**
     * Called to resume event consumption.
     */
    @Override
    public void resume() {

    }

    /**
     * Used to serialize and persist {@link #lastSentSequenceNo} in a configurable interval.
     * @return stateful objects of the processing element as a map
     */
    @Override
    public Map<String, Object> currentState() {
        Map<String, Object> state = new HashMap<>();
        state.put(siddhiAppName, lastSentSequenceNo.get());
        return state;
    }

    /**
     * Used to get the persisted {@link #lastSentSequenceNo} value in case of client connection failure so that
     * replay the missing messages/events.
     * @param map the stateful objects of the processing element as a map.
     */
     @Override
     public void restoreState(Map<String, Object> map) {
         Object seqObject = map.get(siddhiAppName);
         if (seqObject != null) {
             lastSentSequenceNo.set((int) seqObject);
         }
     }

    private void subscribe() {
        try {
            subscription =  streamingConnection.subscribe(destination , stanMessageProcessor , new SubscriptionOptions
                    .Builder().startAtSequence(lastSentSequenceNo.get()).build());
        } catch (IOException | InterruptedException | TimeoutException e) {
            log.error("Error occurred in initializing the Stan receiver for stream: "
                    + sourceEventListener.getStreamDefinition().getId());
            throw new StanInputAdaptorRuntimeException("Error occurred in initializing the Stan receiver for stream: "
                    + sourceEventListener.getStreamDefinition().getId(), e);
        }
    }

    private void initStanProperties() {
        this.destination = optionHolder.validateAndGetStaticValue(StanConstants.DESTINATION, null);
        this.clusterId = optionHolder.validateAndGetStaticValue(StanConstants.CLUSTER_ID, null);
        this.clientId = optionHolder.validateAndGetStaticValue(StanConstants.CLIENT_ID, null);
        this.natsUrl = optionHolder.validateAndGetStaticValue(StanConstants.BOOTSTRAP_SERVERS, null);
    }
}

