package org.wso2.extension.siddhi.io.stan.source;

import org.testng.annotations.Test;
import org.wso2.siddhi.core.SiddhiAppRuntime;
import org.wso2.siddhi.core.SiddhiManager;
import org.wso2.siddhi.core.event.Event;
import org.wso2.siddhi.core.stream.output.StreamCallback;
import org.wso2.siddhi.core.util.EventPrinter;

public class TestCaseOfStanSource {
    @Test
    public void testJMSTopicSource1() throws InterruptedException {
        SiddhiManager siddhiManager = new SiddhiManager();
        String inStreamDefinition = "@App:name(\"SiddhiApp\")"
                + "@source(type='stan', @map(type='xml'), "
                + "destination='testet', "
                + "bootstrap.servers='nats://localhost:4222', "
                + "client.id='stan_client', "
                + "cluster.id='test-cluster'"
                + ")" +
                "define stream inputStream (name string, age int, country string);";

        String query = ("@info(name = 'query1') "
                + "from inputStream "
                + "select *  "
                + "insert into outputStream;");


        SiddhiAppRuntime executionPlanRuntime = siddhiManager.createSiddhiAppRuntime(inStreamDefinition + query);


        executionPlanRuntime.addCallback("inputStream", new StreamCallback() {
            @Override
            public void receive(Event[] events) {
                //System.out.println("hi" + events);
                EventPrinter.print(events);
                for (Event event : events) {
                    System.out.println(event.toString());
                }
            }
        });

        executionPlanRuntime.start();


        Thread.sleep(60000);
    }
}


