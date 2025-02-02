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
package org.apache.iotdb.confignode.manager;

import org.apache.iotdb.common.rpc.thrift.TConsensusGroupId;
import org.apache.iotdb.common.rpc.thrift.TConsensusGroupType;
import org.apache.iotdb.common.rpc.thrift.TDataNodeLocation;
import org.apache.iotdb.common.rpc.thrift.TRegionReplicaSet;
import org.apache.iotdb.common.rpc.thrift.TSStatus;
import org.apache.iotdb.confignode.cli.TemporaryClient;
import org.apache.iotdb.confignode.conf.ConfigNodeConf;
import org.apache.iotdb.confignode.conf.ConfigNodeDescriptor;
import org.apache.iotdb.confignode.consensus.request.read.GetOrCountStorageGroupReq;
import org.apache.iotdb.confignode.consensus.request.write.CreateRegionsReq;
import org.apache.iotdb.confignode.consensus.request.write.SetDataReplicationFactorReq;
import org.apache.iotdb.confignode.consensus.request.write.SetSchemaReplicationFactorReq;
import org.apache.iotdb.confignode.consensus.request.write.SetStorageGroupReq;
import org.apache.iotdb.confignode.consensus.request.write.SetTTLReq;
import org.apache.iotdb.confignode.consensus.request.write.SetTimePartitionIntervalReq;
import org.apache.iotdb.confignode.consensus.response.CountStorageGroupResp;
import org.apache.iotdb.confignode.consensus.response.StorageGroupSchemaResp;
import org.apache.iotdb.confignode.persistence.ClusterSchemaInfo;
import org.apache.iotdb.confignode.persistence.PartitionInfo;
import org.apache.iotdb.consensus.common.response.ConsensusReadResponse;
import org.apache.iotdb.rpc.TSStatusCode;

import java.util.Collections;
import java.util.List;

public class ClusterSchemaManager {

  private static final ConfigNodeConf conf = ConfigNodeDescriptor.getInstance().getConf();
  private static final int schemaReplicationFactor = conf.getSchemaReplicationFactor();
  private static final int dataReplicationFactor = conf.getDataReplicationFactor();
  private static final int initialSchemaRegionCount = conf.getInitialSchemaRegionCount();
  private static final int initialDataRegionCount = conf.getInitialDataRegionCount();

  private static final ClusterSchemaInfo clusterSchemaInfo = ClusterSchemaInfo.getInstance();
  private static final PartitionInfo partitionInfo = PartitionInfo.getInstance();

  private final Manager configManager;

  public ClusterSchemaManager(Manager configManager) {
    this.configManager = configManager;
  }

  /**
   * Set StorageGroup and allocate the default amount Regions
   *
   * @return SUCCESS_STATUS if the StorageGroup is set and region allocation successful.
   *     NOT_ENOUGH_DATA_NODE if there are not enough DataNode for Region allocation.
   *     STORAGE_GROUP_ALREADY_EXISTS if the StorageGroup is already set.
   */
  public TSStatus setStorageGroup(SetStorageGroupReq setStorageGroupReq) {
    TSStatus result;
    if (configManager.getDataNodeManager().getOnlineDataNodeCount()
        < Math.max(initialSchemaRegionCount, initialDataRegionCount)) {
      result = new TSStatus(TSStatusCode.NOT_ENOUGH_DATA_NODE.getStatusCode());
      result.setMessage("DataNode is not enough, please register more.");
    } else {
      if (clusterSchemaInfo.containsStorageGroup(setStorageGroupReq.getSchema().getName())) {
        result = new TSStatus(TSStatusCode.STORAGE_GROUP_ALREADY_EXISTS.getStatusCode());
        result.setMessage(
            String.format(
                "StorageGroup %s is already set.", setStorageGroupReq.getSchema().getName()));
      } else {
        CreateRegionsReq createRegionsReq = new CreateRegionsReq();
        createRegionsReq.setStorageGroup(setStorageGroupReq.getSchema().getName());

        // Allocate default Regions
        allocateRegions(TConsensusGroupType.SchemaRegion, createRegionsReq, setStorageGroupReq);
        allocateRegions(TConsensusGroupType.DataRegion, createRegionsReq, setStorageGroupReq);

        // Persist StorageGroup and Regions
        getConsensusManager().write(setStorageGroupReq);
        result = getConsensusManager().write(createRegionsReq).getStatus();

        // Create Regions in DataNode
        // TODO: use client pool
        for (TRegionReplicaSet regionReplicaSet : createRegionsReq.getRegionReplicaSets()) {
          for (TDataNodeLocation dataNodeLocation : regionReplicaSet.getDataNodeLocations()) {
            switch (regionReplicaSet.getRegionId().getType()) {
              case SchemaRegion:
                TemporaryClient.getInstance()
                    .createSchemaRegion(
                        dataNodeLocation.getDataNodeId(),
                        createRegionsReq.getStorageGroup(),
                        regionReplicaSet);
                break;
              case DataRegion:
                TemporaryClient.getInstance()
                    .createDataRegion(
                        dataNodeLocation.getDataNodeId(),
                        createRegionsReq.getStorageGroup(),
                        regionReplicaSet,
                        setStorageGroupReq.getSchema().getTTL());
            }
          }
        }
      }
    }
    return result;
  }

  /** TODO: Allocate by LoadManager */
  private void allocateRegions(
      TConsensusGroupType type, CreateRegionsReq createRegionsReq, SetStorageGroupReq setSGReq) {

    // TODO: Use CopySet algorithm to optimize region allocation policy

    int replicaCount =
        type.equals(TConsensusGroupType.SchemaRegion)
            ? schemaReplicationFactor
            : dataReplicationFactor;
    int regionCount =
        type.equals(TConsensusGroupType.SchemaRegion)
            ? initialSchemaRegionCount
            : initialDataRegionCount;
    List<TDataNodeLocation> onlineDataNodes = getDataNodeInfoManager().getOnlineDataNodes();
    for (int i = 0; i < regionCount; i++) {
      Collections.shuffle(onlineDataNodes);

      TRegionReplicaSet regionReplicaSet = new TRegionReplicaSet();
      TConsensusGroupId consensusGroupId =
          new TConsensusGroupId(type, partitionInfo.generateNextRegionGroupId());
      regionReplicaSet.setRegionId(consensusGroupId);
      regionReplicaSet.setDataNodeLocations(onlineDataNodes.subList(0, replicaCount));
      createRegionsReq.addRegion(regionReplicaSet);

      switch (type) {
        case SchemaRegion:
          setSGReq.getSchema().addToSchemaRegionGroupIds(consensusGroupId);
          break;
        case DataRegion:
          setSGReq.getSchema().addToDataRegionGroupIds(consensusGroupId);
      }
    }
  }

  /**
   * Get the SchemaRegionGroupIds or DataRegionGroupIds from the specific StorageGroup
   *
   * @param storageGroup StorageGroupName
   * @param type SchemaRegion or DataRegion
   * @return All SchemaRegionGroupIds when type is SchemaRegion, and all DataRegionGroupIds when
   *     type is DataRegion
   */
  public List<TConsensusGroupId> getRegionGroupIds(String storageGroup, TConsensusGroupType type) {
    return clusterSchemaInfo.getRegionGroupIds(storageGroup, type);
  }

  public TSStatus setTTL(SetTTLReq setTTLReq) {
    // TODO: Inform DataNodes
    return getConsensusManager().write(setTTLReq).getStatus();
  }

  public TSStatus setSchemaReplicationFactor(
      SetSchemaReplicationFactorReq setSchemaReplicationFactorReq) {
    // TODO: Inform DataNodes
    return getConsensusManager().write(setSchemaReplicationFactorReq).getStatus();
  }

  public TSStatus setDataReplicationFactor(
      SetDataReplicationFactorReq setDataReplicationFactorReq) {
    // TODO: Inform DataNodes
    return getConsensusManager().write(setDataReplicationFactorReq).getStatus();
  }

  public TSStatus setTimePartitionInterval(
      SetTimePartitionIntervalReq setTimePartitionIntervalReq) {
    // TODO: Inform DataNodes
    return getConsensusManager().write(setTimePartitionIntervalReq).getStatus();
  }

  /**
   * Count StorageGroups by specific path pattern
   *
   * @return CountStorageGroupResp
   */
  public CountStorageGroupResp countMatchedStorageGroups(
      GetOrCountStorageGroupReq countStorageGroupReq) {
    ConsensusReadResponse readResponse = getConsensusManager().read(countStorageGroupReq);
    return (CountStorageGroupResp) readResponse.getDataset();
  }

  /**
   * Get StorageGroupSchemas by specific path pattern
   *
   * @return StorageGroupSchemaDataSet
   */
  public StorageGroupSchemaResp getMatchedStorageGroupSchema(
      GetOrCountStorageGroupReq getStorageGroupReq) {
    ConsensusReadResponse readResponse = getConsensusManager().read(getStorageGroupReq);
    return (StorageGroupSchemaResp) readResponse.getDataset();
  }

  public List<String> getStorageGroupNames() {
    return clusterSchemaInfo.getStorageGroupNames();
  }

  private DataNodeManager getDataNodeInfoManager() {
    return configManager.getDataNodeManager();
  }

  private ConsensusManager getConsensusManager() {
    return configManager.getConsensusManager();
  }
}
