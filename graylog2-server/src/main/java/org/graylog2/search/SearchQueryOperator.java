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
package org.graylog2.search;

import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.regex.Pattern;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

public abstract class SearchQueryOperator {
    public abstract Bson buildBson(String key, Object value);

    @Override
    public boolean equals(Object obj) {
        return obj.getClass().equals(getClass());
    }

    public static class Equals extends SearchQueryOperator {
        @Override
        public Bson buildBson(String key, Object value) {
            return Filters.eq(key, value);
        }
    }

    public static class Regexp extends SearchQueryOperator {
        @Override
        public Bson buildBson(String key, Object value) {
            return Filters.regex(key, Pattern.compile(Pattern.quote(value.toString()), CASE_INSENSITIVE));
        }
    }

    public static class Greater extends SearchQueryOperator {
        @Override
        public Bson buildBson(String key, Object value) {
            return Filters.gt(key, value);
        }
    }

    public static class GreaterEquals extends SearchQueryOperator {
        @Override
        public Bson buildBson(String key, Object value) {
            return Filters.gte(key, value);
        }
    }

    public static class Less extends SearchQueryOperator {
        @Override
        public Bson buildBson(String key, Object value) {
            return Filters.lt(key, value);
        }
    }

    public static class LessEquals extends SearchQueryOperator {
        @Override
        public Bson buildBson(String key, Object value) {
            return Filters.lte(key, value);
        }
    }
}
