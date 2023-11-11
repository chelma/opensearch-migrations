package org.opensearch.migrations.replay;

import org.opensearch.migrations.replay.datatypes.PojoTrafficStreamKey;
import org.opensearch.migrations.replay.datatypes.UniqueReplayerRequestKey;

public class TestRequestKey {
    public final static UniqueReplayerRequestKey getTestConnectionRequestId(int replayerIdx) {
        return new UniqueReplayerRequestKey(
                new PojoTrafficStreamKey("testNodeId", "testConnectionId", 0),
                0, replayerIdx);
    }
}