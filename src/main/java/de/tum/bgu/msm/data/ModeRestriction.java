package de.tum.bgu.msm.data;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static de.tum.bgu.msm.data.Mode.*;

public enum ModeRestriction implements Id {
    Auto,
    AutoPt,
    AutoCycle,
    AutoWalk,
    AutoPtCycle,
    AutoPtWalk,
    AutoPtCycleWalk,
    Pt,
    PtCycle,
    PtWalk,
    PtCycleWalk,
    Cycle,
    CycleWalk,
    Walk;

    @Override
    public int getId(){
        return this.ordinal();
    }

    public EnumSet<Mode> getRestrictedModeSet() {
        switch(this) {
            case Auto:
                return EnumSet.of(autoDriver, autoPassenger);
            case AutoPt:
                return EnumSet.of(autoDriver, autoPassenger, publicTransport);
            case AutoCycle:
                return EnumSet.of(autoDriver, autoPassenger, bicycle);
            case AutoWalk:
                return EnumSet.of(autoDriver, autoPassenger, walk);
            case AutoPtCycle:
                return EnumSet.of(autoDriver, autoPassenger, publicTransport, bicycle);
            case AutoPtWalk:
                return EnumSet.of(autoDriver, autoPassenger, publicTransport, walk);
            case AutoPtCycleWalk:
                return EnumSet.of(autoDriver, autoPassenger, publicTransport, bicycle, walk);
            case Pt:
                return EnumSet.of(publicTransport);
            case PtCycle:
                return EnumSet.of(publicTransport, bicycle);
            case PtWalk:
                return EnumSet.of(publicTransport, walk);
            case PtCycleWalk:
                return EnumSet.of(publicTransport, bicycle, walk);
            case Cycle:
                return EnumSet.of(bicycle);
            case CycleWalk:
                return EnumSet.of(bicycle, walk);
            case Walk:
                return EnumSet.of(walk);
            default:
                return null;
        }
    }

    public static List<ModeRestriction> getWalkModeRestriction(){
        List<ModeRestriction> list = new ArrayList<>();
        list.add(AutoWalk);
        list.add(AutoPtWalk);
        list.add(AutoPtCycleWalk);
        list.add(PtWalk);
        list.add(PtCycleWalk);
        list.add(CycleWalk);
        list.add(Walk);
        return list;
    }
}
