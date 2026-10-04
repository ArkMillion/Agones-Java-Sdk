package cn.arkmillion.agones.internal;

import cn.arkmillion.agones.model.GameServer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GameServerMapper {
    private GameServerMapper() { }

    public static GameServer fromProto(cn.arkmillion.agones.internal.proto.GameServer value) {
        cn.arkmillion.agones.internal.proto.GameServer.ObjectMeta m = value.getObjectMeta();
        GameServer.ObjectMeta meta = new GameServer.ObjectMeta(m.getName(), m.getNamespace(), m.getUid(),
                m.getResourceVersion(), m.getGeneration(), timestamp(m.getCreationTimestamp()),
                timestamp(m.getDeletionTimestamp()), m.getAnnotationsMap(), m.getLabelsMap());
        cn.arkmillion.agones.internal.proto.GameServer.Spec.Health h = value.getSpec().getHealth();
        GameServer.Spec spec = new GameServer.Spec(new GameServer.Health(h.getDisabled(), h.getPeriodSeconds(),
                h.getFailureThreshold(), h.getInitialDelaySeconds()));
        cn.arkmillion.agones.internal.proto.GameServer.Status s = value.getStatus();
        ArrayList<GameServer.Address> addresses = new ArrayList<GameServer.Address>();
        for (cn.arkmillion.agones.internal.proto.GameServer.Status.Address a : s.getAddressesList()) {
            addresses.add(new GameServer.Address(a.getType(), a.getAddress()));
        }
        ArrayList<GameServer.Port> ports = new ArrayList<GameServer.Port>();
        for (cn.arkmillion.agones.internal.proto.GameServer.Status.Port p : s.getPortsList()) {
            ports.add(new GameServer.Port(p.getName(), p.getPort()));
        }
        cn.arkmillion.agones.internal.proto.GameServer.Status.PlayerStatus p = s.getPlayers();
        GameServer.PlayerStatus players = new GameServer.PlayerStatus(p.getCount(), p.getCapacity(), p.getIdsList());
        Map<String, GameServer.CounterStatus> counters = new LinkedHashMap<String, GameServer.CounterStatus>();
        for (Map.Entry<String, cn.arkmillion.agones.internal.proto.GameServer.Status.CounterStatus> e : s.getCountersMap().entrySet()) {
            counters.put(e.getKey(), new GameServer.CounterStatus(e.getValue().getCount(), e.getValue().getCapacity()));
        }
        Map<String, GameServer.ListStatus> lists = new LinkedHashMap<String, GameServer.ListStatus>();
        for (Map.Entry<String, cn.arkmillion.agones.internal.proto.GameServer.Status.ListStatus> e : s.getListsMap().entrySet()) {
            lists.put(e.getKey(), new GameServer.ListStatus(e.getValue().getCapacity(), e.getValue().getValuesList()));
        }
        return new GameServer(meta, spec, new GameServer.Status(s.getState(), s.getAddress(), addresses, ports,
                players, counters, lists));
    }

    private static Instant timestamp(long epochSeconds) { return epochSeconds == 0 ? null : Instant.ofEpochSecond(epochSeconds); }
}

