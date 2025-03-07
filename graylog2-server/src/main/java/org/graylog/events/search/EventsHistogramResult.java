/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
package org.graylog.events.search;

import java.time.ZonedDateTime;
import java.util.List;

public record EventsHistogramResult(EventsBuckets buckets) {
    public static EventsHistogramResult fromResult(MoreSearch.Histogram result) {
        final var events = result.buckets().events().stream()
                .map(event -> new Bucket(event.startDate(), event.count()))
                .toList();
        final var alerts = result.buckets().alerts().stream()
                .map(alert -> new Bucket(alert.startDate(), alert.count()))
                .toList();

        return new EventsHistogramResult(new EventsBuckets(events, alerts));
    }

    public record EventsBuckets(List<Bucket> events, List<Bucket> alerts) {}

    public record Bucket(ZonedDateTime startDate, Long count) {}
}
