package com.dwinovo.anima.telemetry.session;

import com.dwinovo.anima.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

public final class SessionSavedData extends SavedData {

    private static final String SESSION_ID_KEY = "anima_session_id";
    public static final String DATA_NAME = Constants.MOD_ID + "_session";

    private String animaSessionId;

    private SessionSavedData() {
        this(null);
    }

    private SessionSavedData(String animaSessionId) {
        this.animaSessionId = sanitize(animaSessionId);
    }

    public static SavedData.Factory<SessionSavedData> factory() {
        return new SavedData.Factory<>(SessionSavedData::new, SessionSavedData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    }

    private static SessionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        return new SessionSavedData(tag.getString(SESSION_ID_KEY));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (this.animaSessionId != null) {
            tag.putString(SESSION_ID_KEY, this.animaSessionId);
        }
        return tag;
    }

    public boolean hasSessionId() {
        return this.animaSessionId != null;
    }

    public String getOrCreateSessionId() {
        if (this.animaSessionId == null) {
            this.animaSessionId = UUID.randomUUID().toString();
            this.setDirty();
        }
        return this.animaSessionId;
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}

