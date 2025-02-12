// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.distribution.Group;
import com.yahoo.vdslib.distribution.GroupVisitor;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import com.yahoo.vespa.config.content.StorDistributionConfig;
import org.junit.jupiter.api.Test;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.yahoo.vdslib.state.NodeType.DISTRIBUTOR;
import static com.yahoo.vdslib.state.NodeType.STORAGE;
import static com.yahoo.vdslib.state.State.DOWN;
import static com.yahoo.vdslib.state.State.INITIALIZING;
import static com.yahoo.vdslib.state.State.UP;
import static com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest.Condition.FORCE;
import static com.yahoo.vespa.clustercontroller.utils.staterestapi.requests.SetUnitStateRequest.Condition.SAFE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.yahoo.vespa.clustercontroller.core.NodeStateChangeChecker.Result;

public class NodeStateChangeCheckerTest {

    private static final int requiredRedundancy = 4;
    private static final int currentClusterStateVersion = 2;

    private static final Node nodeDistributor = new Node(DISTRIBUTOR, 1);
    private static final Node nodeStorage = new Node(STORAGE, 1);

    private static final NodeState UP_NODE_STATE = new NodeState(STORAGE, UP);
    private static final NodeState MAINTENANCE_NODE_STATE = createNodeState(State.MAINTENANCE, "Orchestrator");
    private static final NodeState DOWN_NODE_STATE = createNodeState(DOWN, "RetireEarlyExpirer");

    private static final HierarchicalGroupVisiting noopVisiting = new HierarchicalGroupVisiting() {
        @Override public boolean isHierarchical() { return false; }
        @Override public void visit(GroupVisitor visitor) { }
    };

    private static NodeState createNodeState(State state, String description) {
        return new NodeState(STORAGE, state).setDescription(description);
    }

    private static ClusterState clusterState(String state) {
        try {
            return new ClusterState(state);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static ClusterState defaultAllUpClusterState() {
        return clusterState(String.format("version:%d distributor:4 storage:4", currentClusterStateVersion));
    }

    private NodeStateChangeChecker createChangeChecker(ContentCluster cluster) {
        return new NodeStateChangeChecker(requiredRedundancy, noopVisiting, cluster.clusterInfo(),
                                          false, cluster.maxNumberOfGroupsAllowedToBeDown());
    }

    private ContentCluster createCluster(int nodeCount) {
        Collection<ConfiguredNode> nodes = createNodes(nodeCount);
        Distribution distribution = mock(Distribution.class);
        Group group = new Group(2, "two");
        when(distribution.getRootGroup()).thenReturn(group);
        return new ContentCluster("Clustername", nodes, distribution);
    }

    private String createDistributorHostInfo(int replicationfactor1, int replicationfactor2, int replicationfactor3) {
        return "{\n" +
                "    \"cluster-state-version\": 2,\n" +
                "    \"distributor\": {\n" +
                "        \"storage-nodes\": [\n" +
                "            {\n" +
                "                \"node-index\": 0,\n" +
                "                \"min-current-replication-factor\": " + replicationfactor1 + "\n" +
                "            },\n" +
                "            {\n" +
                "                \"node-index\": 1,\n" +
                "                \"min-current-replication-factor\": " + replicationfactor2 + "\n" +
                "            },\n" +
                "            {\n" +
                "                \"node-index\": 2,\n" +
                "                \"min-current-replication-factor\": " + replicationfactor3 + "\n" +
                "            },\n" +
                "            {\n" +
                "                \"node-index\": 3\n" +
                "            }\n" +
                "        ]\n" +
                "    }\n" +
                "}\n";
    }

    private void markAllNodesAsReportingStateUp(ContentCluster cluster) {
        final ClusterInfo clusterInfo = cluster.clusterInfo();
        final int configuredNodeCount = cluster.clusterInfo().getConfiguredNodes().size();
        for (int i = 0; i < configuredNodeCount; i++) {
            clusterInfo.getDistributorNodeInfo(i).setReportedState(new NodeState(DISTRIBUTOR, UP), 0);
            clusterInfo.getDistributorNodeInfo(i).setHostInfo(HostInfo.createHostInfo(createDistributorHostInfo(4, 5, 6)));
            clusterInfo.getStorageNodeInfo(i).setReportedState(new NodeState(STORAGE, UP), 0);
        }
    }

    @Test
    void testCanUpgradeForce() {
        var nodeStateChangeChecker = createChangeChecker(createCluster(1));
        NodeState newState = new NodeState(STORAGE, INITIALIZING);
        Result result = nodeStateChangeChecker.evaluateTransition(
                nodeDistributor, defaultAllUpClusterState(), FORCE,
                UP_NODE_STATE, newState);
        assertTrue(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    @Test
    void testDeniedInMoratorium() {
        ContentCluster cluster = createCluster(4);
        var nodeStateChangeChecker = new NodeStateChangeChecker(
                requiredRedundancy, noopVisiting, cluster.clusterInfo(), true, cluster.maxNumberOfGroupsAllowedToBeDown());
        Result result = nodeStateChangeChecker.evaluateTransition(
                new Node(STORAGE, 10), defaultAllUpClusterState(), SAFE,
                UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("Master cluster controller is bootstrapping and in moratorium", result.getReason());
    }

    @Test
    void testUnknownStorageNode() {
        ContentCluster cluster = createCluster(4);
        var nodeStateChangeChecker = createChangeChecker(cluster);
        Result result = nodeStateChangeChecker.evaluateTransition(
                new Node(STORAGE, 10), defaultAllUpClusterState(), SAFE,
                UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("Unknown node storage.10", result.getReason());
    }

    @Test
    void testSafeMaintenanceDisallowedWhenOtherStorageNodeInFlatClusterIsSuspended() {
        // Nodes 0-3, storage node 0 being in maintenance with "Orchestrator" description.
        ContentCluster cluster = createCluster(4);
        cluster.clusterInfo().getStorageNodeInfo(0).setWantedState(new NodeState(STORAGE, State.MAINTENANCE).setDescription("Orchestrator"));
        var nodeStateChangeChecker = createChangeChecker(cluster);
        ClusterState clusterStateWith0InMaintenance = clusterState(String.format(
                "version:%d distributor:4 storage:4 .0.s:m",
                currentClusterStateVersion));

        Result result = nodeStateChangeChecker.evaluateTransition(
                new Node(STORAGE, 1), clusterStateWith0InMaintenance,
                SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("At most one node can have a wanted state when #groups = 1: Other storage node 0 has wanted state Maintenance",
                result.getReason());
    }

    @Test
    void testSafeMaintenanceDisallowedWhenOtherDistributorInFlatClusterIsSuspended() {
        // Nodes 0-3, storage node 0 being in maintenance with "Orchestrator" description.
        ContentCluster cluster = createCluster(4);
        cluster.clusterInfo().getDistributorNodeInfo(0)
                .setWantedState(new NodeState(DISTRIBUTOR, DOWN).setDescription("Orchestrator"));
        var nodeStateChangeChecker = createChangeChecker(cluster);
        ClusterState clusterStateWith0InMaintenance = clusterState(String.format(
                "version:%d distributor:4 .0.s:d storage:4",
                currentClusterStateVersion));

        Result result = nodeStateChangeChecker.evaluateTransition(
                new Node(STORAGE, 1), clusterStateWith0InMaintenance,
                SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("At most one node can have a wanted state when #groups = 1: Other distributor 0 has wanted state Down",
                result.getReason());
    }

    @Test
    void testSafeMaintenanceDisallowedWhenDistributorInGroupIsDown() {
        // Nodes 0-3, distributor 0 being in maintenance with "Orchestrator" description.
        // 2 groups: nodes 0-1 is group 0, 2-3 is group 1.
        ContentCluster cluster = createCluster(4);
        cluster.clusterInfo().getDistributorNodeInfo(0)
                .setWantedState(new NodeState(STORAGE, DOWN).setDescription("Orchestrator"));
        HierarchicalGroupVisiting visiting = makeHierarchicalGroupVisitingWith2Groups(4);
        var nodeStateChangeChecker = new NodeStateChangeChecker(
                requiredRedundancy, visiting, cluster.clusterInfo(), false, cluster.maxNumberOfGroupsAllowedToBeDown());
        ClusterState clusterStateWith0InMaintenance = clusterState(String.format(
                "version:%d distributor:4 .0.s:d storage:4",
                currentClusterStateVersion));

        {
            // Denied for node 2 in group 1, since distributor 0 in group 0 is down
            Result result = nodeStateChangeChecker.evaluateTransition(
                    new Node(STORAGE, 2), clusterStateWith0InMaintenance,
                    SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
            assertFalse(result.settingWantedStateIsAllowed());
            assertFalse(result.wantedStateAlreadySet());
            assertEquals("At most one group can have wanted state: Other distributor 0 in group 0 has wanted state Down", result.getReason());
        }

        {
            // Even node 1 of group 0 is not permitted, as node 0 is not considered
            // suspended since only the distributor has been set down.
            Result result = nodeStateChangeChecker.evaluateTransition(
                    new Node(STORAGE, 1), clusterStateWith0InMaintenance,
                    SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
            assertFalse(result.settingWantedStateIsAllowed(), result.getReason());
            assertEquals("Another distributor wants state DOWN: 0", result.getReason());
        }
    }

    @Test
    void testSafeMaintenanceWhenOtherStorageNodeInGroupIsSuspended() {
        // Nodes 0-3, storage node 0 being in maintenance with "Orchestrator" description.
        // 2 groups: nodes 0-1 is group 0, 2-3 is group 1.
        ContentCluster cluster = createCluster(4);
        cluster.clusterInfo().getStorageNodeInfo(0).setWantedState(new NodeState(STORAGE, State.MAINTENANCE).setDescription("Orchestrator"));
        HierarchicalGroupVisiting visiting = makeHierarchicalGroupVisitingWith2Groups(4);
        var nodeStateChangeChecker = new NodeStateChangeChecker(
                requiredRedundancy, visiting, cluster.clusterInfo(), false, cluster.maxNumberOfGroupsAllowedToBeDown());
        ClusterState clusterStateWith0InMaintenance = clusterState(String.format(
                "version:%d distributor:4 storage:4 .0.s:m",
                currentClusterStateVersion));

        {
            // Denied for node 2 in group 1, since node 0 in group 0 is in maintenance
            Result result = nodeStateChangeChecker.evaluateTransition(
                    new Node(STORAGE, 2), clusterStateWith0InMaintenance,
                    SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
            assertFalse(result.settingWantedStateIsAllowed());
            assertFalse(result.wantedStateAlreadySet());
            assertEquals("At most one group can have wanted state: Other storage node 0 in group 0 has wanted state Maintenance",
                    result.getReason());
        }

        {
            // Permitted for node 1 in group 0, since node 0 is already in maintenance with
            // description Orchestrator, and it is in the same group
            Result result = nodeStateChangeChecker.evaluateTransition(
                    new Node(STORAGE, 1), clusterStateWith0InMaintenance,
                    SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
            assertTrue(result.settingWantedStateIsAllowed(), result.getReason());
            assertFalse(result.wantedStateAlreadySet());
        }
    }

    /**
     * Make a HierarchicalGroupVisiting with the given number of nodes, with 2 groups:
     * Group "0" is nodes 0-1, group "1" is 2-3.
     */
    private HierarchicalGroupVisiting makeHierarchicalGroupVisitingWith2Groups(int nodes) {
        int groups = 2;
        if (nodes % groups != 0) {
            throw new IllegalArgumentException("Cannot have 2 groups with an odd number of nodes: " + nodes);
        }
        int nodesPerGroup = nodes / groups;

        var configBuilder = new StorDistributionConfig.Builder()
                .active_per_leaf_group(true)
                .ready_copies(2)
                .redundancy(2)
                .initial_redundancy(2);

        configBuilder.group(new StorDistributionConfig.Group.Builder()
                .index("invalid")
                .name("invalid")
                .capacity(nodes)
                .partitions("1|*"));

        int nodeIndex = 0;
        for (int i = 0; i < groups; ++i) {
            var groupBuilder = new StorDistributionConfig.Group.Builder()
                    .index(String.valueOf(i))
                    .name(String.valueOf(i))
                    .capacity(nodesPerGroup)
                    .partitions("");
            for (int j = 0; j < nodesPerGroup; ++j, ++nodeIndex) {
                groupBuilder.nodes(new StorDistributionConfig.Group.Nodes.Builder()
                        .index(nodeIndex));
            }
            configBuilder.group(groupBuilder);
        }

        return new HierarchicalGroupVisitingAdapter(new Distribution(configBuilder.build()));
    }

    @Test
    void testSafeSetStateDistributors() {
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(createCluster(1));
        Result result = nodeStateChangeChecker.evaluateTransition(
                nodeDistributor, defaultAllUpClusterState(), SAFE,
                UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertTrue(result.getReason().contains("Safe-set of node state is only supported for storage nodes"));
    }

    @Test
    void testCanUpgradeSafeMissingStorage() {
        // Create a content cluster with 4 nodes, and storage node with index 3 down.
        ContentCluster cluster = createCluster(4);
        setAllNodesUp(cluster, HostInfo.createHostInfo(createDistributorHostInfo(4, 5, 6)));
        cluster.clusterInfo().getStorageNodeInfo(3).setReportedState(new NodeState(STORAGE, DOWN), 0);
        ClusterState clusterStateWith3Down = clusterState(String.format(
                "version:%d distributor:4 storage:4 .3.s:d",
                currentClusterStateVersion));

        // We should then be denied setting storage node 1 safely to maintenance.
        var nodeStateChangeChecker = createChangeChecker(cluster);
        Result result = nodeStateChangeChecker.evaluateTransition(
                nodeStorage, clusterStateWith3Down, SAFE,
                UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("Another storage node has state DOWN: 3", result.getReason());
    }

    @Test
    void testCanUpgradeStorageSafeYes() {
        Result result = transitionToMaintenanceWithNoStorageNodesDown(createCluster(4), defaultAllUpClusterState());
        assertTrue(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    @Test
    void testSetUpFailsIfReportedIsDown() {
        ContentCluster cluster = createCluster(4);
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);
        // Not setting nodes up -> all are down

        Result result = nodeStateChangeChecker.evaluateTransition(
                nodeStorage, defaultAllUpClusterState(), SAFE,
                MAINTENANCE_NODE_STATE, UP_NODE_STATE);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    // A node may be reported as Up but have a generated state of Down if it's part of
    // nodes taken down implicitly due to a group having too low node availability.
    @Test
    void testSetUpSucceedsIfReportedIsUpButGeneratedIsDown() {
        ContentCluster cluster = createCluster(4);
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);

        markAllNodesAsReportingStateUp(cluster);

        ClusterState stateWithNodeDown = clusterState(String.format(
                "version:%d distributor:4 storage:4 .%d.s:d",
                currentClusterStateVersion, nodeStorage.getIndex()));

        Result result = nodeStateChangeChecker.evaluateTransition(
                nodeStorage, stateWithNodeDown, SAFE,
                MAINTENANCE_NODE_STATE, UP_NODE_STATE);
        assertTrue(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    @Test
    void testCanSetUpEvenIfOldWantedStateIsDown() {
        ContentCluster cluster = createCluster(4);
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);
        setAllNodesUp(cluster, HostInfo.createHostInfo(createDistributorHostInfo(4, 3, 6)));

        Result result = nodeStateChangeChecker.evaluateTransition(
                nodeStorage, defaultAllUpClusterState(), SAFE,
                new NodeState(STORAGE, DOWN), UP_NODE_STATE);
        assertTrue(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    @Test
    void testCanUpgradeStorageSafeNo() {
        ContentCluster cluster = createCluster(4);
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);
        setAllNodesUp(cluster, HostInfo.createHostInfo(createDistributorHostInfo(4, 3, 6)));

        Result result = nodeStateChangeChecker.evaluateTransition(
                nodeStorage, defaultAllUpClusterState(), SAFE,
                UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("Distributor 0 says storage node 1 has buckets with redundancy as low as 3, but we require at least 4",
                result.getReason());
    }

    @Test
    void testCanUpgradeIfMissingMinReplicationFactor() {
        ContentCluster cluster = createCluster(4);
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);
        setAllNodesUp(cluster, HostInfo.createHostInfo(createDistributorHostInfo(4, 3, 6)));

        Result result = nodeStateChangeChecker.evaluateTransition(
                new Node(STORAGE, 3), defaultAllUpClusterState(), SAFE,
                UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertTrue(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    @Test
    void testCanUpgradeIfStorageNodeMissingFromNodeInfo() {
        ContentCluster cluster = createCluster(4);
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);
        String hostInfo = "{\n" +
                "    \"cluster-state-version\": 2,\n" +
                "    \"distributor\": {\n" +
                "        \"storage-nodes\": [\n" +
                "            {\n" +
                "                \"node-index\": 0,\n" +
                "                \"min-current-replication-factor\": " + requiredRedundancy + "\n" +
                "            }\n" +
                "        ]\n" +
                "    }\n" +
                "}\n";
        setAllNodesUp(cluster, HostInfo.createHostInfo(hostInfo));

        Result result = nodeStateChangeChecker.evaluateTransition(
                new Node(STORAGE, 1), defaultAllUpClusterState(), SAFE,
                UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertTrue(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    @Test
    void testMissingDistributorState() {
        ContentCluster cluster = createCluster(4);
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);
        cluster.clusterInfo().getStorageNodeInfo(1).setReportedState(new NodeState(STORAGE, UP), 0);

        Result result = nodeStateChangeChecker.evaluateTransition(
                nodeStorage, defaultAllUpClusterState(), SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("Distributor node 0 has not reported any cluster state version yet.", result.getReason());
    }

    private Result transitionToSameState(State state, String oldDescription, String newDescription) {
        ContentCluster cluster = createCluster(4);
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);

        NodeState currentNodeState = createNodeState(state, oldDescription);
        NodeState newNodeState = createNodeState(state, newDescription);
        return nodeStateChangeChecker.evaluateTransition(
                nodeStorage, defaultAllUpClusterState(), SAFE,
                currentNodeState, newNodeState);
    }

    private Result transitionToSameState(String oldDescription, String newDescription) {
        return transitionToSameState(State.MAINTENANCE, oldDescription, newDescription);
    }

    @Test
    void testSettingUpWhenUpCausesAlreadySet() {
        Result result = transitionToSameState(UP, "foo", "bar");
        assertTrue(result.wantedStateAlreadySet());
    }

    @Test
    void testSettingAlreadySetState() {
        Result result = transitionToSameState("foo", "foo");
        assertFalse(result.settingWantedStateIsAllowed());
        assertTrue(result.wantedStateAlreadySet());
    }

    @Test
    void testDifferentDescriptionImpliesDenied() {
        Result result = transitionToSameState("foo", "bar");
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    private Result transitionToMaintenanceWithOneStorageNodeDown(ContentCluster cluster, ClusterState clusterState) {
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);

        for (int x = 0; x < cluster.clusterInfo().getConfiguredNodes().size(); x++) {
            State state = UP;
            cluster.clusterInfo().getDistributorNodeInfo(x).setReportedState(new NodeState(DISTRIBUTOR, state), 0);
            cluster.clusterInfo().getDistributorNodeInfo(x).setHostInfo(HostInfo.createHostInfo(createDistributorHostInfo(4, 5, 6)));
            cluster.clusterInfo().getStorageNodeInfo(x).setReportedState(new NodeState(STORAGE, state), 0);
        }

        return nodeStateChangeChecker.evaluateTransition(
                nodeStorage, clusterState, SAFE, UP_NODE_STATE, MAINTENANCE_NODE_STATE);
    }

    private void setAllNodesUp(ContentCluster cluster, HostInfo distributorHostInfo) {
        for (int x = 0; x < cluster.clusterInfo().getConfiguredNodes().size(); x++) {
            State state = UP;
            cluster.clusterInfo().getDistributorNodeInfo(x).setReportedState(new NodeState(DISTRIBUTOR, state), 0);
            cluster.clusterInfo().getDistributorNodeInfo(x).setHostInfo(distributorHostInfo);
            cluster.clusterInfo().getStorageNodeInfo(x).setReportedState(new NodeState(STORAGE, state), 0);
        }
    }

    private Result transitionToMaintenanceWithNoStorageNodesDown(ContentCluster cluster, ClusterState clusterState) {
        return transitionToMaintenanceWithOneStorageNodeDown(cluster, clusterState);
    }

    @Test
    void testCanUpgradeWhenAllUp() {
        Result result = transitionToMaintenanceWithNoStorageNodesDown(createCluster(4), defaultAllUpClusterState());
        assertTrue(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    @Test
    void testCanUpgradeWhenAllUpOrRetired() {
        Result result = transitionToMaintenanceWithNoStorageNodesDown(createCluster(4), defaultAllUpClusterState());
        assertTrue(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    @Test
    void testCanUpgradeWhenStorageIsDown() {
        ClusterState clusterState = defaultAllUpClusterState();
        var storageNodeIndex = nodeStorage.getIndex();

        ContentCluster cluster = createCluster(4);
        NodeState downNodeState = new NodeState(STORAGE, DOWN);
        cluster.clusterInfo().getStorageNodeInfo(storageNodeIndex).setReportedState(downNodeState, 4 /* time */);
        clusterState.setNodeState(new Node(STORAGE, storageNodeIndex), downNodeState);

        Result result = transitionToMaintenanceWithOneStorageNodeDown(cluster, clusterState);
        assertTrue(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    @Test
    void testCannotUpgradeWhenOtherStorageIsDown() {
        int otherIndex = 2;
        // If this fails, just set otherIndex to some other valid index.
        assertNotEquals(nodeStorage.getIndex(), otherIndex);

        ContentCluster cluster = createCluster(4);
        ClusterState clusterState = defaultAllUpClusterState();
        NodeState downNodeState = new NodeState(STORAGE, DOWN);
        cluster.clusterInfo().getStorageNodeInfo(otherIndex).setReportedState(downNodeState, 4 /* time */);
        clusterState.setNodeState(new Node(STORAGE, otherIndex), downNodeState);

        Result result = transitionToMaintenanceWithOneStorageNodeDown(cluster, clusterState);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertTrue(result.getReason().contains("Another storage node has state DOWN: 2"));
    }

    @Test
    void testNodeRatioRequirementConsidersGeneratedNodeStates() {
        ContentCluster cluster = createCluster(4);
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);

        markAllNodesAsReportingStateUp(cluster);

        // Both minRatioOfStorageNodesUp and minStorageNodesUp imply that a single node being
        // in state Down should halt the upgrade. This must also take into account the generated
        // state, not just the reported state. In this case, all nodes are reported as being Up
        // but one node has a generated state of Down.
        ClusterState stateWithNodeDown = clusterState(String.format(
                "version:%d distributor:4 storage:4 .3.s:d",
                currentClusterStateVersion));

        Result result = nodeStateChangeChecker.evaluateTransition(
                nodeStorage, stateWithNodeDown, SAFE,
                UP_NODE_STATE, MAINTENANCE_NODE_STATE);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    @Test
    void testDownDisallowedByNonRetiredState() {
        Result result = evaluateDownTransition(
                defaultAllUpClusterState(),
                UP,
                currentClusterStateVersion,
                0);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("Only retired nodes are allowed to be set to DOWN in safe mode - is Up", result.getReason());
    }

    @Test
    void testDownDisallowedByBuckets() {
        Result result = evaluateDownTransition(
                retiredClusterStateSuffix(),
                UP,
                currentClusterStateVersion,
                1);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("The storage node manages 1 buckets", result.getReason());
    }

    @Test
    void testDownDisallowedByReportedState() {
        Result result = evaluateDownTransition(
                retiredClusterStateSuffix(),
                INITIALIZING,
                currentClusterStateVersion,
                0);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("Reported state (Initializing) is not UP, so no bucket data is available", result.getReason());
    }

    @Test
    void testDownDisallowedByVersionMismatch() {
        Result result = evaluateDownTransition(
                retiredClusterStateSuffix(),
                UP,
                currentClusterStateVersion - 1,
                0);
        assertFalse(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
        assertEquals("Cluster controller at version 2 got info for storage node 1 at a different version 1",
                result.getReason());
    }

    @Test
    void testAllowedToSetDown() {
        Result result = evaluateDownTransition(
                retiredClusterStateSuffix(),
                UP,
                currentClusterStateVersion,
                0);
        assertTrue(result.settingWantedStateIsAllowed());
        assertFalse(result.wantedStateAlreadySet());
    }

    private Result evaluateDownTransition(ClusterState clusterState,
                                          State reportedState,
                                          int hostInfoClusterStateVersion,
                                          int lastAlldisksBuckets) {
        ContentCluster cluster = createCluster(4);
        NodeStateChangeChecker nodeStateChangeChecker = createChangeChecker(cluster);

        StorageNodeInfo nodeInfo = cluster.clusterInfo().getStorageNodeInfo(nodeStorage.getIndex());
        nodeInfo.setReportedState(new NodeState(STORAGE, reportedState), 0);
        nodeInfo.setHostInfo(createHostInfoWithMetrics(hostInfoClusterStateVersion, lastAlldisksBuckets));

        return nodeStateChangeChecker.evaluateTransition(
                nodeStorage, clusterState, SAFE,
                UP_NODE_STATE, DOWN_NODE_STATE);
    }

    private ClusterState retiredClusterStateSuffix() {
        return clusterState(String.format("version:%d distributor:4 storage:4 .%d.s:r",
                currentClusterStateVersion,
                nodeStorage.getIndex()));
    }

    private static HostInfo createHostInfoWithMetrics(int clusterStateVersion, int lastAlldisksBuckets) {
        return HostInfo.createHostInfo(String.format("{\n" +
                        "  \"metrics\":\n" +
                        "  {\n" +
                        "    \"snapshot\":\n" +
                        "    {\n" +
                        "      \"from\":1494940706,\n" +
                        "      \"to\":1494940766\n" +
                        "    },\n" +
                        "    \"values\":\n" +
                        "    [\n" +
                        "      {\n" +
                        "        \"name\":\"vds.datastored.alldisks.buckets\",\n" +
                        "        \"description\":\"buckets managed\",\n" +
                        "        \"values\":\n" +
                        "        {\n" +
                        "          \"average\":262144.0,\n" +
                        "          \"count\":1,\n" +
                        "          \"rate\":0.016666,\n" +
                        "          \"min\":262144,\n" +
                        "          \"max\":262144,\n" +
                        "          \"last\":%d\n" +
                        "        },\n" +
                        "        \"dimensions\":\n" +
                        "        {\n" +
                        "        }\n" +
                        "      },\n" +
                        "      {\n" +
                        "        \"name\":\"vds.datastored.alldisks.docs\",\n" +
                        "        \"description\":\"documents stored\",\n" +
                        "        \"values\":\n" +
                        "        {\n" +
                        "          \"average\":154689587.0,\n" +
                        "          \"count\":1,\n" +
                        "          \"rate\":0.016666,\n" +
                        "          \"min\":154689587,\n" +
                        "          \"max\":154689587,\n" +
                        "          \"last\":154689587\n" +
                        "        },\n" +
                        "        \"dimensions\":\n" +
                        "        {\n" +
                        "        }\n" +
                        "      },\n" +
                        "      {\n" +
                        "        \"name\":\"vds.datastored.bucket_space.buckets_total\",\n" +
                        "        \"description\":\"Total number buckets present in the bucket space (ready + not ready)\",\n" +
                        "        \"values\":\n" +
                        "        {\n" +
                        "          \"average\":0.0,\n" +
                        "          \"sum\":0.0,\n" +
                        "          \"count\":1,\n" +
                        "          \"rate\":0.016666,\n" +
                        "          \"min\":0,\n" +
                        "          \"max\":0,\n" +
                        "          \"last\":0\n" +
                        "        },\n" +
                        "        \"dimensions\":\n" +
                        "        {\n" +
                        "          \"bucketSpace\":\"global\"\n" +
                        "        }\n" +
                        "      },\n" +
                        "      {\n" +
                        "        \"name\":\"vds.datastored.bucket_space.buckets_total\",\n" +
                        "        \"description\":\"Total number buckets present in the bucket space (ready + not ready)\",\n" +
                        "        \"values\":\n" +
                        "        {\n" +
                        "          \"average\":129.0,\n" +
                        "          \"sum\":129.0,\n" +
                        "          \"count\":1,\n" +
                        "          \"rate\":0.016666,\n" +
                        "          \"min\":129,\n" +
                        "          \"max\":129,\n" +
                        "          \"last\":%d\n" +
                        "        },\n" +
                        "        \"dimensions\":\n" +
                        "        {\n" +
                        "          \"bucketSpace\":\"default\"\n" +
                        "        }\n" +
                        "      }\n" +
                        "    ]\n" +
                        "  },\n" +
                        "  \"cluster-state-version\":%d\n" +
                        "}",
                lastAlldisksBuckets, lastAlldisksBuckets, clusterStateVersion));
    }

    private List<ConfiguredNode> createNodes(int count) {
        List<ConfiguredNode> nodes = new ArrayList<>();
        for (int i = 0; i < count; i++)
            nodes.add(new ConfiguredNode(i, false));
        return nodes;
    }
}
