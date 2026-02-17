package com.dwinovo.anima.telemetry.model;

public record EventRequest(
    MetaRequest meta,
    WhenRequest when,
    WhereRequest where,
    WhoRequest who,
    EventBodyRequest event
) {
    public record MetaRequest(
        String session_id
    ) {}

    public record WhenRequest(
        String iso8601,
        long epoch_millis,
        long game_time
    ) {}

    public record WhereRequest(
        String dimension,
        double x,
        double y,
        double z
    ) {}

    public record WhoRequest(
        String entity_uuid,
        String entity_name,
        String entity_type
    ) {}

    public record ActorRequest(
        String id,
        String name,
        String type
    ) {}

    public record DetailsRequest(
        String damage_type,
        float damage_amount,
        String damage_source_entity_type
    ) {}

    public record EventBodyRequest(
        ActorRequest subject,
        String action,
        ActorRequest object,
        DetailsRequest details,
        String raw_text
    ) {}
}
