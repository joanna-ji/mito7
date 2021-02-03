package de.tum.bgu.msm.data;

public enum Day implements Id {
    weekday,
    saturday,
    sunday;

    @Override
    public int getId() {
        return this.ordinal();
    }
}
