package com.dreamdisplays.datatypes;

import java.util.UUID;

public record SyncPacket(UUID id, boolean isSync, boolean currentState, long currentTime, long limitTime) {
}
