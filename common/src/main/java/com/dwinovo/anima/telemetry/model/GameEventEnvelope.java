package com.dwinovo.anima.telemetry.model;

public class GameEventEnvelope {

    public Meta meta;
    public When when;
    public Where where;
    public Who who;
    public Event event;

    public static class Meta {
        public String authToken;
        public String protocolVersion;

        public Meta(String authToken, String protocolVersion) {
            this.authToken = authToken;
            this.protocolVersion = protocolVersion;
        }
    }

    public static class When {
        public String iso8601;
        public long epochMillis;
        public long gameTime;

        public When(String iso8601, long epochMillis, long gameTime) {
            this.iso8601 = iso8601;
            this.epochMillis = epochMillis;
            this.gameTime = gameTime;
        }
    }

    public static class Where {
        public String dimension;
        public double x;
        public double y;
        public double z;

        public Where(String dimension, double x, double y, double z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static class Who {
        public String observerId;
        public String observerName;
        public String perspective;

        public Who(String observerId, String observerName, String perspective) {
            this.observerId = observerId;
            this.observerName = observerName;
            this.perspective = perspective;
        }
    }

    public static class Event {
        public Actor subject;
        public String action;
        public Actor object;
        public Details details;

        public Event(Actor subject, String action, Actor object, Details details) {
            this.subject = subject;
            this.action = action;
            this.object = object;
            this.details = details;
        }
    }

    public static class Actor {
        public String id;
        public String name;
        public String type;

        public Actor(String id, String name, String type) {
            this.id = id;
            this.name = name;
            this.type = type;
        }
    }

    public static class Details {
        public String damageType;
        public String damageSourceEntityType;
        public String deathMessage;

        public Details(String damageType, String damageSourceEntityType, String deathMessage) {
            this.damageType = damageType;
            this.damageSourceEntityType = damageSourceEntityType;
            this.deathMessage = deathMessage;
        }
    }
}
