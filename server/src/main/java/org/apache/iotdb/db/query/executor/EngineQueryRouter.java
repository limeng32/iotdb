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

package org.apache.iotdb.db.query.executor;

import org.apache.iotdb.db.exception.StorageEngineException;
import org.apache.iotdb.db.exception.metadata.MetadataException;
import org.apache.iotdb.db.exception.query.QueryProcessException;
import org.apache.iotdb.db.qp.physical.crud.AggregationPlan;
import org.apache.iotdb.db.qp.physical.crud.FillQueryPlan;
import org.apache.iotdb.db.qp.physical.crud.GroupByPlan;
import org.apache.iotdb.db.qp.physical.crud.QueryPlan;
import org.apache.iotdb.db.query.context.QueryContext;
import org.apache.iotdb.db.query.dataset.groupby.GroupByWithValueFilterDataSet;
import org.apache.iotdb.db.query.dataset.groupby.GroupByWithoutValueFilterDataSet;
import org.apache.iotdb.db.query.fill.IFill;
import org.apache.iotdb.tsfile.exception.filter.QueryFilterOptimizationException;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.read.expression.ExpressionType;
import org.apache.iotdb.tsfile.read.expression.IExpression;
import org.apache.iotdb.tsfile.read.expression.impl.BinaryExpression;
import org.apache.iotdb.tsfile.read.expression.impl.GlobalTimeExpression;
import org.apache.iotdb.tsfile.read.expression.util.ExpressionOptimizer;
import org.apache.iotdb.tsfile.read.filter.GroupByFilter;
import org.apache.iotdb.tsfile.read.query.dataset.QueryDataSet;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Query entrance class of IoTDB query process. All query clause will be transformed to physical
 * plan, physical plan will be executed by EngineQueryRouter.
 */
public class EngineQueryRouter implements IEngineQueryRouter {

  protected DataQueryExecutorFactory executorFactory = EngineExecutor::new;

  @Override
  public QueryDataSet query(QueryPlan queryPlan, QueryContext context)
      throws StorageEngineException {
    IExpression expression = queryPlan.getExpression();
    List<Path> deduplicatedPaths = queryPlan.getDeduplicatedPaths();
    List<TSDataType> deduplicatedDataTypes = queryPlan.getDeduplicatedDataTypes();

    if (expression != null) {
      try {
        IExpression optimizedExpression = ExpressionOptimizer.getInstance()
            .optimize(expression, deduplicatedPaths);
        DataQueryExecutor dataQueryExecutor = executorFactory.getExecutor(deduplicatedPaths, deduplicatedDataTypes,
            optimizedExpression);
        if (optimizedExpression.getType() == ExpressionType.GLOBAL_TIME) {
          return dataQueryExecutor.executeWithoutValueFilter(context);
        } else {
          return dataQueryExecutor.executeWithValueFilter(context);
        }

      } catch (QueryFilterOptimizationException | IOException e) {
        throw new StorageEngineException(e.getMessage());
      }
    } else {
      DataQueryExecutor dataQueryExecutor = executorFactory.getExecutor(deduplicatedPaths,
          deduplicatedDataTypes, null);
      try {
        return dataQueryExecutor.executeWithoutValueFilter(context);
      } catch (IOException e) {
        throw new StorageEngineException(e.getMessage());
      }
    }
  }

  @Override
  public QueryDataSet aggregate(AggregationPlan aggregationPlan, QueryContext context)
      throws QueryFilterOptimizationException, StorageEngineException, QueryProcessException, IOException {
    IExpression expression = aggregationPlan.getExpression();
    List<Path> selectedSeries = aggregationPlan.getDeduplicatedPaths();
    try {
      if (expression != null) {
        IExpression optimizedExpression = ExpressionOptimizer.getInstance()
            .optimize(expression, selectedSeries);
        AggregateEngineExecutor engineExecutor = new AggregateEngineExecutor(
            aggregationPlan);
        if (optimizedExpression.getType() == ExpressionType.GLOBAL_TIME) {
          return engineExecutor.executeWithoutValueFilter(context);
        } else {
          return engineExecutor.executeWithValueFilter(context);
        }
      } else {
        AggregateEngineExecutor engineExecutor = new AggregateEngineExecutor(
            aggregationPlan);
        return engineExecutor.executeWithoutValueFilter(context);
      }
    } catch (MetadataException e) {
      throw new QueryProcessException(e);
    }
  }


  @Override
  public QueryDataSet groupBy(GroupByPlan groupByPlan, QueryContext context)
      throws QueryFilterOptimizationException, StorageEngineException, QueryProcessException, IOException {
    long unit = groupByPlan.getUnit();
    long slidingStep = groupByPlan.getSlidingStep();
    long startTime = groupByPlan.getStartTime();
    long endTime = groupByPlan.getEndTime();

    IExpression expression = groupByPlan.getExpression();
    List<Path> selectedSeries = groupByPlan.getDeduplicatedPaths();

    GlobalTimeExpression timeExpression = new GlobalTimeExpression(
        new GroupByFilter(unit, slidingStep, startTime, endTime));

    if (expression == null) {
      expression = timeExpression;
    } else {
      expression = BinaryExpression.and(expression, timeExpression);
    }

    IExpression optimizedExpression = ExpressionOptimizer.getInstance()
        .optimize(expression, selectedSeries);
    try {
      if (optimizedExpression.getType() == ExpressionType.GLOBAL_TIME) {
        return new GroupByWithoutValueFilterDataSet(context,
            groupByPlan);
      } else {
        return new GroupByWithValueFilterDataSet(context,
            groupByPlan);
      }
    } catch (MetadataException e) {
      throw new QueryProcessException(e);
    }
  }

  @Override
  public QueryDataSet fill(FillQueryPlan fillQueryPlan, QueryContext context)
      throws StorageEngineException, QueryProcessException, IOException {
    List<Path> fillPaths = fillQueryPlan.getDeduplicatedPaths();
    List<TSDataType> dataTypes = fillQueryPlan.getDeduplicatedDataTypes();
    long queryTime = fillQueryPlan.getQueryTime();
    Map<TSDataType, IFill> fillType = fillQueryPlan.getFillType();

    FillEngineExecutor fillEngineExecutor = new FillEngineExecutor(fillPaths, dataTypes, queryTime,
        fillType);
    return fillEngineExecutor.execute(context);
  }

}
