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
package org.graylog.events.processor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.graylog.events.context.EventDefinitionContextService;
import org.graylog.events.fields.EventFieldSpec;
import org.graylog.events.notifications.EventNotificationHandler;
import org.graylog.events.notifications.EventNotificationSettings;
import org.graylog.events.processor.storage.EventStorageHandler;
import org.graylog2.database.DbEntity;
import org.graylog2.shared.security.RestPermissions;
import org.joda.time.DateTime;

import javax.annotation.Nullable;
import java.util.Set;

@DbEntity(readPermission = RestPermissions.EVENT_DEFINITIONS_READ, collection = DBEventDefinitionService.COLLECTION_NAME)
public interface EventDefinition {
    enum State {
        ENABLED,
        DISABLED
    }

    @Nullable
    String id();

    String title();

    String description();

    default String remediationSteps() {
        return null;
    }

    @Nullable
    DateTime updatedAt();

    @Nullable
    DateTime matchedAt();

    int priority();

    boolean alert();

    EventProcessorConfig config();

    ImmutableMap<String, EventFieldSpec> fieldSpec();

    ImmutableList<String> keySpec();

    EventNotificationSettings notificationSettings();

    ImmutableList<EventNotificationHandler.Config> notifications();

    ImmutableList<EventStorageHandler.Config> storage();

    EventDefinitionContextService.SchedulerCtx schedulerCtx();

    default Set<String> requiredPermissions() {
        return config().requiredPermissions();
    }

    default String eventProcedureId() {
        return null;
    }
}
