package com.mikoalopex.createfirefightingadd.content.kinetics.coupling;

public enum CouplingInterfaceMode {
    FREE, ACTIVE, PASSIVE;

    public boolean accepts(CouplingInterfaceMode other) {
        return this == FREE ? other == FREE : this != other && other != FREE;
    }
}
