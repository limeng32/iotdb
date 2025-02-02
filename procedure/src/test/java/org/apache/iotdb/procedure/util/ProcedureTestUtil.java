/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.procedure.util;

import org.apache.iotdb.procedure.ProcedureExecutor;
import org.apache.iotdb.procedure.scheduler.ProcedureScheduler;
import org.apache.iotdb.procedure.store.IProcedureStore;

import java.util.concurrent.TimeUnit;

public class ProcedureTestUtil {
  public static void waitForProcedure(ProcedureExecutor executor, long... procIds) {
    for (long procId : procIds) {
      long startTimeForProcId = System.currentTimeMillis();
      while (executor.isRunning()
          && !executor.isFinished(procId)
          && TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startTimeForProcId)
              < 35) {
        sleepWithoutInterrupt(250);
      }
    }
  }

  public static void sleepWithoutInterrupt(final long timeToSleep) {
    long currentTime = System.currentTimeMillis();
    long endTime = timeToSleep + currentTime;
    boolean interrupted = false;
    while (currentTime < endTime) {
      try {
        Thread.sleep(endTime - currentTime);
      } catch (InterruptedException e) {
        interrupted = true;
      }
      currentTime = System.currentTimeMillis();
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  public static void stopService(
      ProcedureExecutor procExecutor, ProcedureScheduler scheduler, IProcedureStore store) {
    procExecutor.stop();
    procExecutor.join();
    scheduler.clear();
    scheduler.stop();
    store.stop();
  }
}
