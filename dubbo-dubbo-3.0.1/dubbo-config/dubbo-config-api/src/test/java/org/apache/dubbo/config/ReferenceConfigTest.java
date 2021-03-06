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
package org.apache.dubbo.config;

import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.config.annotation.Argument;
import org.apache.dubbo.config.annotation.Method;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.api.DemoService;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.config.provider.impl.DemoServiceImpl;

import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.apache.dubbo.rpc.Constants.LOCAL_PROTOCOL;
import static org.apache.dubbo.rpc.Constants.SCOPE_REMOTE;

public class ReferenceConfigTest {
    private TestingServer zkServer;
    private String registryUrl;

    @BeforeEach
    public void setUp() throws Exception {
        DubboBootstrap.reset();
        int zkServerPort = NetUtils.getAvailablePort(NetUtils.getRandomPort());
        this.zkServer = new TestingServer(zkServerPort, true);
        this.zkServer.start();
        this.registryUrl = "zookeeper://localhost:" + zkServerPort;
    }

    @AfterEach
    public void tearDown() throws IOException {
        DubboBootstrap.reset();
        zkServer.stop();
    }

    @Test
    @Disabled("Disabled due to Github Actions environment")
    public void testInjvm() throws Exception {
        ApplicationConfig application = new ApplicationConfig();
        application.setName("test-protocol-random-port");
        application.setEnableFileCache(false);
        ApplicationModel.getConfigManager().setApplication(application);

        RegistryConfig registry = new RegistryConfig();
        registry.setAddress(registryUrl);

        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setName("dubbo");

        ServiceConfig<DemoService> demoService;
        demoService = new ServiceConfig<DemoService>();
        demoService.setInterface(DemoService.class);
        demoService.setRef(new DemoServiceImpl());
        demoService.setRegistry(registry);
        demoService.setProtocol(protocol);

        ReferenceConfig<DemoService> rc = new ReferenceConfig<DemoService>();
        rc.setRegistry(registry);
        rc.setInterface(DemoService.class.getName());
        rc.setScope(SCOPE_REMOTE);

        try {
            System.setProperty("java.net.preferIPv4Stack", "true");
            demoService.export();
            rc.get();
            Assertions.assertTrue(!LOCAL_PROTOCOL.equalsIgnoreCase(
                rc.getInvoker().getUrl().getProtocol()));
        } finally {
            System.clearProperty("java.net.preferIPv4Stack");
            rc.destroy();
            demoService.unexport();
        }

        // Manually trigger dubbo resource recycling.
        DubboBootstrap.getInstance().destroy();
    }

    /**
     * unit test for dubbo-1765
     */
    @Test
    public void test1ReferenceRetry() {
        ApplicationConfig application = new ApplicationConfig();
        application.setName("test-reference-retry");
        application.setEnableFileCache(false);
        ApplicationModel.getConfigManager().setApplication(application);

        RegistryConfig registry = new RegistryConfig();
        registry.setAddress(registryUrl);
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setName("mockprotocol");

        ReferenceConfig<DemoService> rc = new ReferenceConfig<DemoService>();
        rc.setRegistry(registry);
        rc.setInterface(DemoService.class.getName());

        boolean success = false;
        DemoService demoService = null;
        try {
            demoService = rc.get();
            success = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        Assertions.assertFalse(success);
        Assertions.assertNull(demoService);

        ServiceConfig<DemoService> sc = new ServiceConfig<DemoService>();
        sc.setInterface(DemoService.class);
        sc.setRef(new DemoServiceImpl());
        sc.setRegistry(registry);
        sc.setProtocol(protocol);

        try {
            System.setProperty("java.net.preferIPv4Stack", "true");
            sc.export();
            demoService = rc.get();
            success = true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            rc.destroy();
            sc.unexport();
            System.clearProperty("java.net.preferIPv4Stack");
        }
        Assertions.assertTrue(success);
        Assertions.assertNotNull(demoService);

    }

    @Test
    public void testMetaData() {
        ReferenceConfig config = new ReferenceConfig();
        Map<String, String> metaData = config.getMetaData();
        Assertions.assertEquals(0, metaData.size(), "Expect empty metadata but found: " + metaData);

        // test merged and override consumer attributes
        ConsumerConfig consumerConfig = new ConsumerConfig();
        consumerConfig.setAsync(true);
        consumerConfig.setActives(10);
        config.setConsumer(consumerConfig);
        config.setAsync(false);// override

        metaData = config.getMetaData();
        Assertions.assertEquals(2, metaData.size());
        Assertions.assertEquals("" + consumerConfig.getActives(), metaData.get("actives"));
        Assertions.assertEquals("" + config.isAsync(), metaData.get("async"));

    }

    @Test
    public void testGetPrefixes() {

        ReferenceConfig referenceConfig = new ReferenceConfig();
        referenceConfig.setInterface(DemoService.class);

        List<String> prefixes = referenceConfig.getPrefixes();
        Assertions.assertTrue(prefixes.contains("dubbo.reference." + referenceConfig.getInterface()));

        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            referenceConfig.getPrefixes();
        }
        long end = System.currentTimeMillis();
        System.out.println("ReferenceConfig get prefixes cost: " + (end - start));

    }

    @Test
    public void testConstructWithReferenceAnnotation() throws NoSuchFieldException {
        Reference reference = getClass().getDeclaredField("innerTest").getAnnotation(Reference.class);
        ReferenceConfig referenceConfig = new ReferenceConfig(reference);
        Assertions.assertEquals(1, referenceConfig.getMethods().size());
        Assertions.assertEquals(((MethodConfig) referenceConfig.getMethods().get(0)).getName(), "sayHello");
        Assertions.assertEquals(1300, (int) ((MethodConfig) referenceConfig.getMethods().get(0)).getTimeout());
        Assertions.assertEquals(4, (int) ((MethodConfig) referenceConfig.getMethods().get(0)).getRetries());
        Assertions.assertEquals(((MethodConfig) referenceConfig.getMethods().get(0)).getLoadbalance(), "random");
        Assertions.assertEquals(3, (int) ((MethodConfig) referenceConfig.getMethods().get(0)).getActives());
        Assertions.assertEquals(5, (int) ((MethodConfig) referenceConfig.getMethods().get(0)).getExecutes());
        Assertions.assertTrue(((MethodConfig) referenceConfig.getMethods().get(0)).isAsync());
        Assertions.assertEquals(((MethodConfig) referenceConfig.getMethods().get(0)).getOninvokeMethod(), "i");
        Assertions.assertEquals(((MethodConfig) referenceConfig.getMethods().get(0)).getOnreturnMethod(), "r");
        Assertions.assertEquals(((MethodConfig) referenceConfig.getMethods().get(0)).getOnthrowMethod(), "t");
        Assertions.assertEquals(((MethodConfig) referenceConfig.getMethods().get(0)).getCache(), "c");
    }


    @Reference(methods = {@Method(name = "sayHello", timeout = 1300, retries = 4, loadbalance = "random", async = true,
        actives = 3, executes = 5, deprecated = true, sticky = true, oninvoke = "instance.i", onthrow = "instance.t", onreturn = "instance.r", cache = "c", validation = "v",
        arguments = {@Argument(index = 24, callback = true, type = "sss")})})
    private InnerTest innerTest;

    private class InnerTest {

    }
}
