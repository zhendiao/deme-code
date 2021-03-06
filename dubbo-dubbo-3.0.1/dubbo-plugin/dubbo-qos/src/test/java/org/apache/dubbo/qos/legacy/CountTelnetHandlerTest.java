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
package org.apache.dubbo.qos.legacy;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.qos.legacy.channel.MockChannel;
import org.apache.dubbo.qos.legacy.service.DemoService;
import org.apache.dubbo.remoting.telnet.TelnetHandler;
import org.apache.dubbo.remoting.telnet.support.TelnetUtils;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcStatus;
import org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

public class CountTelnetHandlerTest {

    private TelnetHandler handler = new CountTelnetHandler();
    private MockChannel mockChannel;
    private Invoker<DemoService> mockInvoker;
    private URL url = URL.valueOf("dubbo://127.0.0.1:20884/demo");
    private CountDownLatch latch;

    @BeforeEach
    public void setUp() {
        latch = new CountDownLatch(2);
        mockChannel = new MockChannel(url, latch);
        mockInvoker = mock(Invoker.class);
        given(mockInvoker.getInterface()).willReturn(DemoService.class);
        given(mockInvoker.getUrl()).willReturn(url);
    }

    @AfterEach
    public void tearDown() {
        ProtocolUtils.closeAll();
        mockChannel.close();
        reset(mockInvoker);
    }

    @Test
    public void test() throws Exception {
        String methodName = "sayHello";
        String message = "org.apache.dubbo.qos.legacy.service.DemoService sayHello 1";

        DubboProtocol.getDubboProtocol().export(mockInvoker);
        RpcStatus.beginCount(url, methodName);
        RpcStatus.endCount(url, methodName, 10L, true);
        handler.telnet(mockChannel, message);
        latch.await();

        StringBuilder sb = new StringBuilder();
        for (Object o : mockChannel.getReceivedObjects()) {
            sb.append(o.toString());
        }

        assertThat(sb.toString(), containsString(buildTable(methodName,
            10, 10, "1", "0", "0")));
    }

    public static String buildTable(String methodName, long averageElapsed,
                                    long maxElapsed, String total, String failed, String active) {
        List<String> header = new LinkedList<>();
        header.add("method");
        header.add("total");
        header.add("failed");
        header.add("active");
        header.add("average");
        header.add("max");

        List<List<String>> table = new LinkedList<>();
        List<String> row = new LinkedList<String>();
        row.add(methodName);
        row.add(total);
        row.add(failed);
        row.add(active);
        row.add(averageElapsed + "ms");
        row.add(maxElapsed + "ms");

        table.add(row);

        return TelnetUtils.toTable(header, table);
    }

}
