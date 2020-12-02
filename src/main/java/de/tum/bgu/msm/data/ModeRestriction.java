package de.tum.bgu.msm.data;

import java.util.EnumSet;

public enum ModeRestriction implements Id {
    auto,
    autoPtWalk,
    autoPtWalkCycle,
    ptWalk,
    ptWalkCycle;

    @Override
    public int getId(){
        return this.ordinal();
    }

    public EnumSet<Mode> getRestrictedModeSet() {
        switch(this) {
            case auto:
                return EnumSet.of(Mode.autoDriver,Mode.autoPassenger);
            case autoPtWalk:
                return EnumSet.of(Mode.autoDriver,Mode.autoPassenger, Mode.publicTransport, Mode.walk);
            case autoPtWalkCycle:
                return EnumSet.of(Mode.autoDriver,Mode.autoPassenger, Mode.publicTransport, Mode.walk, Mode.bicycle);
            case ptWalk:
                return EnumSet.of(Mode.publicTransport, Mode.walk);
            case ptWalkCycle:
                return EnumSet.of(Mode.publicTransport, Mode.walk, Mode.bicycle);
            default:
                return null;
        }
    }
}
