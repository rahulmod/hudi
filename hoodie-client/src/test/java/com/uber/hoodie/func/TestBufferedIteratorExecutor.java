/*
 * Copyright (c) 2018 Uber Technologies, Inc. (hoodie-dev-group@uber.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uber.hoodie.func;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uber.hoodie.common.HoodieTestDataGenerator;
import com.uber.hoodie.common.model.HoodieRecord;
import com.uber.hoodie.common.table.timeline.HoodieActiveTimeline;
import com.uber.hoodie.config.HoodieWriteConfig;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestBufferedIteratorExecutor {

  private final HoodieTestDataGenerator hoodieTestDataGenerator = new HoodieTestDataGenerator();
  private final String commitTime = HoodieActiveTimeline.createNewCommitTime();
  private ExecutorService executorService = null;

  @Before
  public void beforeTest() {
    this.executorService = Executors.newFixedThreadPool(1);
  }

  @After
  public void afterTest() {
    if (this.executorService != null) {
      this.executorService.shutdownNow();
      this.executorService = null;
    }
  }

  @Test
  public void testExecutor() throws Exception {

    final List<HoodieRecord> hoodieRecords = hoodieTestDataGenerator.generateInserts(commitTime, 100);

    HoodieWriteConfig hoodieWriteConfig = mock(HoodieWriteConfig.class);
    when(hoodieWriteConfig.getWriteBufferLimitBytes()).thenReturn(1024);
    BufferedIteratorExecutor bufferedIteratorExecutor = new BufferedIteratorExecutor(hoodieWriteConfig,
        hoodieRecords.iterator(), LazyInsertIterable.bufferedItrPayloadTransform(HoodieTestDataGenerator.avroSchema),
        executorService);
    Function<BufferedIterator, Integer> function = (bufferedIterator) -> {
      Integer count = 0;
      while (bufferedIterator.hasNext()) {
        count++;
        bufferedIterator.next();
      }
      return count;
    };
    Future<Integer> future = bufferedIteratorExecutor.start(function);
    // It should buffer and write 100 records
    Assert.assertEquals((int) future.get(), 100);
    // There should be no remaining records in the buffer
    Assert.assertFalse(bufferedIteratorExecutor.isRemaining());
  }
}
