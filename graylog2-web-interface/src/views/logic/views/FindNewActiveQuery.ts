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

// Returns a new query ID, in case the active query gets deleted.
import type { List } from 'immutable';

const FindNewActiveQueryId = (queryIds: List<string>, activeQueryId: string, removedQueryIds: List<string>) => {
  const currentQueryIdIndex = queryIds.indexOf(activeQueryId);
  const priorQueryIds = queryIds.slice(0, currentQueryIdIndex).toList();
  const listToPickNewIdFrom = priorQueryIds.isEmpty() ? queryIds : priorQueryIds.reverse();

  return listToPickNewIdFrom.find((queryId) => !removedQueryIds.includes(queryId));
};

export default FindNewActiveQueryId;
