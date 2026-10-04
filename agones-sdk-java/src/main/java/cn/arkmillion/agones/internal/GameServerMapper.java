package cn.arkmillion.agones.internal;

import cn.arkmillion.agones.model.Address;
import cn.arkmillion.agones.model.Counter;
import cn.arkmillion.agones.model.GameServer;
import cn.arkmillion.agones.model.GameServerList;
import cn.arkmillion.agones.model.GameServerSpec;
import cn.arkmillion.agones.model.GameServerStatus;
import cn.arkmillion.agones.model.HealthConfiguration;
import cn.arkmillion.agones.model.ObjectMeta;
import cn.arkmillion.agones.model.PlayerStatus;
import cn.arkmillion.agones.model.Port;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GameServerMapper {
    private GameServerMapper() { }

    public static GameServer fromProto(cn.arkmillion.agones.internal.proto.GameServer value) {
        cn.arkmillion.agones.internal.proto.GameServer.ObjectMeta m = value.getObjectMeta();
        ObjectMeta meta = new ObjectMeta(m.getName(), m.getNamespace(), m.getUid(),
                m.getResourceVersion(), m.getGeneration(), timestamp(m.getCreationTimestamp()),
                timestamp(m.getDeletionTimestamp()), m.getAnnotationsMap(), m.getLabelsMap());
        cn.arkmillion.agones.internal.proto.GameServer.Spec.Health h = value.getSpec().getHealth();
        GameServerSpec spec = new GameServerSpec(new HealthConfiguration(h.getDisabled(), h.getPeriodSeconds(),
                h.getFailureThreshold(), h.getInitialDelaySeconds()));
        cn.arkmillion.agones.internal.proto.GameServer.Status s = value.getStatus();
        ArrayList<Address> addresses = new ArrayList<Address>();
        for (cn.arkmillion.agones.internal.proto.GameServer.Status.Address a : s.getAddressesList()) {
            addresses.add(new Address(a.getType(), a.getAddress()));
        }
        ArrayList<Port> ports = new ArrayList<Port>();
        for (cn.arkmillion.agones.internal.proto.GameServer.Status.Port p : s.getPortsList()) {
            ports.add(new Port(p.getName(), p.getPort()));
        }
        cn.arkmillion.agones.internal.proto.GameServer.Status.PlayerStatus p = s.getPlayers();
        PlayerStatus players = new PlayerStatus(p.getCount(), p.getCapacity(), p.getIdsList());
        Map<String, Counter> counters = new LinkedHashMap<String, Counter>();
        for (Map.Entry<String, cn.arkmillion.agones.internal.proto.GameServer.Status.CounterStatus> e : s.getCountersMap().entrySet()) {
            counters.put(e.getKey(), new Counter(e.getKey(), e.getValue().getCount(), e.getValue().getCapacity()));
        }
        Map<String, GameServerList> lists = new LinkedHashMap<String, GameServerList>();
        for (Map.Entry<String, cn.arkmillion.agones.internal.proto.GameServer.Status.ListStatus> e : s.getListsMap().entrySet()) {
            lists.put(e.getKey(), new GameServerList(e.getKey(), e.getValue().getCapacity(), e.getValue().getValuesList()));
        }
        return new GameServer(meta, spec, new GameServerStatus(s.getState(), s.getAddress(), addresses, ports,
                players, counters, lists));
    }

    private static Instant timestamp(long epochSeconds) { return epochSeconds == 0 ? null : Instant.ofEpochSecond(epochSeconds); }
}

