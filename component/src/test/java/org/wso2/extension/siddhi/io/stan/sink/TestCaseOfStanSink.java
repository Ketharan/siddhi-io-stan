package org.wso2.extension.siddhi.io.stan.sink;

import org.testng.annotations.Test;
import org.wso2.siddhi.core.SiddhiAppRuntime;
import org.wso2.siddhi.core.SiddhiManager;
import org.wso2.siddhi.core.stream.input.InputHandler;

public class TestCaseOfStanSink {
    /**
     * Test for configure the JMS Sink publish the message to an ActiveMQ topic.
     */
    @Test
    public void jmsPublishTest() throws InterruptedException {
        SiddhiAppRuntime executionPlanRuntime = null;
        //ResultContainer resultContainer = new ResultContainer(2);
        //JMSClient client = new JMSClient("activemq", "DAS_JMS_OUTPUT_TEST", "", resultContainer);
        SiddhiManager siddhiManager = new SiddhiManager();

        //init
        //Thread listenerThread = new Thread(client);
        //listenerThread.start();
        //Thread.sleep(1000);
        // deploying the execution plan



        String inStreamDefinition = "" +
                "@sink(type='stan', @map(type='xml'), "
                + "destination='tests', "
                + "bootstrap.servers='nats://localhost:4222',"
                + "client.id='stan_client',"
                + "cluster.id='test-cluster'"
                + ")" +
                "define stream inputStream (name string, age int, country string);";
        executionPlanRuntime = siddhiManager.
                createSiddhiAppRuntime(inStreamDefinition);
        InputHandler inputStream = executionPlanRuntime.getInputHandler("inputStream");
        executionPlanRuntime.start();
        inputStream.send(new Object[]{"JAMES", 23, "USA"});
        inputStream.send(new Object[]{"MIKE", 23, "Germany"});
        //Assert.assertTrue(resultContainer.assertMessageContent("JAMES"));
        //Assert.assertTrue(resultContainer.assertMessageContent("MIKE"));


        //client.shutdown();


        siddhiManager.shutdown();
    }

}

