/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.tools;

import kafka.test.ClusterInstance;
import kafka.test.annotation.ClusterTest;
import kafka.test.junit.ClusterTestExtensions;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.MockAdminClient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 60)
@ExtendWith(value = ClusterTestExtensions.class)
public class ClusterToolTest {

    @ClusterTest
    public void testClusterId(ClusterInstance clusterInstance) {
        String output = ToolsTestUtils.captureStandardOut(() ->
                assertDoesNotThrow(() -> ClusterTool.execute("cluster-id", "--bootstrap-server", clusterInstance.bootstrapServers())));
        assertTrue(output.contains("Cluster ID: " + clusterInstance.clusterId()));
    }

    @ClusterTest(brokers = 3)
    public void testUnregister(ClusterInstance clusterInstance) {
        int brokerId;
        if (!clusterInstance.isKRaftTest()) {
            brokerId = assertDoesNotThrow(() -> clusterInstance.brokerIds().stream().findFirst().get());
        } else {
            Set<Integer> brokerIds = clusterInstance.brokerIds();
            brokerIds.removeAll(clusterInstance.controllerIds());
            brokerId = assertDoesNotThrow(() -> brokerIds.stream().findFirst().get());
        }
        clusterInstance.shutdownBroker(brokerId);
        String output = ToolsTestUtils.captureStandardOut(() ->
                assertDoesNotThrow(() -> ClusterTool.execute("unregister", "--bootstrap-server", clusterInstance.bootstrapServers(), "--id", String.valueOf(brokerId))));

        if (clusterInstance.isKRaftTest()) {
            assertTrue(output.contains("Broker " + brokerId + " is no longer registered."));
        } else {
            assertTrue(output.contains("The target cluster does not support the broker unregistration API."));
        }
    }

    @Test
    public void testPrintClusterId() throws Exception {
        Admin adminClient = new MockAdminClient.Builder().
                clusterId("QtNwvtfVQ3GEFpzOmDEE-w").
                build();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ClusterTool.clusterIdCommand(new PrintStream(stream), adminClient);
        assertEquals("Cluster ID: QtNwvtfVQ3GEFpzOmDEE-w\n", stream.toString());
    }

    @Test
    public void testClusterTooOldToHaveId() throws Exception {
        Admin adminClient = new MockAdminClient.Builder().
                clusterId(null).
                build();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ClusterTool.clusterIdCommand(new PrintStream(stream), adminClient);
        assertEquals("No cluster ID found. The Kafka version is probably too old.\n", stream.toString());
    }

    @Test
    public void testUnregisterBroker() throws Exception {
        Admin adminClient = new MockAdminClient.Builder().numBrokers(3).
                usingRaftController(true).
                build();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ClusterTool.unregisterCommand(new PrintStream(stream), adminClient, 0);
        assertEquals("Broker 0 is no longer registered.\n", stream.toString());
    }

    @Test
    public void testLegacyModeClusterCannotUnregisterBroker() throws Exception {
        Admin adminClient = new MockAdminClient.Builder().numBrokers(3).
                usingRaftController(false).
                build();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ClusterTool.unregisterCommand(new PrintStream(stream), adminClient, 0);
        assertEquals("The target cluster does not support the broker unregistration API.\n", stream.toString());
    }
}
