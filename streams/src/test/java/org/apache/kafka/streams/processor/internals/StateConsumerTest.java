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
package org.apache.kafka.streams.processor.internals;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.utils.LogContext;
import org.apache.kafka.common.utils.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class StateConsumerTest {

    private final TopicPartition topicOne = new TopicPartition("topic-one", 1);
    private final TopicPartition topicTwo = new TopicPartition("topic-two", 1);
    private final MockConsumer<byte[], byte[]> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
    private final Map<TopicPartition, Long> partitionOffsets = new HashMap<>();
    private final LogContext logContext = new LogContext("test ");
    private GlobalStreamThread.StateConsumer stateConsumer;
    private TaskStub stateMaintainer;

    @BeforeEach
    public void setUp() {
        partitionOffsets.put(topicOne, 20L);
        partitionOffsets.put(topicTwo, 30L);
        stateMaintainer = new TaskStub(partitionOffsets);
        stateConsumer = new GlobalStreamThread.StateConsumer(logContext, consumer, stateMaintainer, Duration.ofMillis(10L));
    }

    @Test
    public void shouldAssignPartitionsToConsumer() {
        stateConsumer.initialize();
        assertEquals(Utils.mkSet(topicOne, topicTwo), consumer.assignment());
    }

    @Test
    public void shouldSeekToInitialOffsets() {
        stateConsumer.initialize();
        assertEquals(20L, consumer.position(topicOne));
        assertEquals(30L, consumer.position(topicTwo));
    }

    @Test
    public void shouldUpdateStateWithReceivedRecordsForPartition() {
        stateConsumer.initialize();
        consumer.addRecord(new ConsumerRecord<>("topic-one", 1, 20L, new byte[0], new byte[0]));
        consumer.addRecord(new ConsumerRecord<>("topic-one", 1, 21L, new byte[0], new byte[0]));
        stateConsumer.pollAndUpdate();
        assertEquals(2, stateMaintainer.updatedPartitions.get(topicOne).intValue());
        assertTrue(stateMaintainer.flushed);
    }

    @Test
    public void shouldUpdateStateWithReceivedRecordsForAllTopicPartition() {
        stateConsumer.initialize();
        consumer.addRecord(new ConsumerRecord<>("topic-one", 1, 20L, new byte[0], new byte[0]));
        consumer.addRecord(new ConsumerRecord<>("topic-two", 1, 31L, new byte[0], new byte[0]));
        consumer.addRecord(new ConsumerRecord<>("topic-two", 1, 32L, new byte[0], new byte[0]));
        stateConsumer.pollAndUpdate();
        assertEquals(1, stateMaintainer.updatedPartitions.get(topicOne).intValue());
        assertEquals(2, stateMaintainer.updatedPartitions.get(topicTwo).intValue());
        assertTrue(stateMaintainer.flushed);
    }

    @Test
    public void shouldCloseConsumer() throws IOException {
        stateConsumer.close(false);
        assertTrue(consumer.closed());
    }

    @Test
    public void shouldCloseStateMaintainer() throws IOException {
        stateConsumer.close(false);
        assertTrue(stateMaintainer.closed);
    }

    @Test
    public void shouldWipeStoreOnClose() throws IOException {
        stateConsumer.close(true);
        assertTrue(stateMaintainer.wipeStore);
    }

    private static class TaskStub implements GlobalStateMaintainer {
        private final Map<TopicPartition, Long> partitionOffsets;
        private final Map<TopicPartition, Integer> updatedPartitions = new HashMap<>();
        private boolean flushed;
        private boolean wipeStore;
        private boolean closed;

        TaskStub(final Map<TopicPartition, Long> partitionOffsets) {
            this.partitionOffsets = partitionOffsets;
        }

        @Override
        public Map<TopicPartition, Long> initialize() {
            return partitionOffsets;
        }

        public void flushState() {
            flushed = true;
        }

        @Override
        public void close(final boolean wipeStateStore) {
            closed = true;
            wipeStore = wipeStateStore;
        }

        @Override
        public void update(final ConsumerRecord<byte[], byte[]> record) {
            final TopicPartition tp = new TopicPartition(record.topic(), record.partition());
            if (!updatedPartitions.containsKey(tp)) {
                updatedPartitions.put(tp, 0);
            }
            updatedPartitions.put(tp, updatedPartitions.get(tp) + 1);
        }

        @Override
        public void maybeCheckpoint() {
            flushState();
        }
    }

}
