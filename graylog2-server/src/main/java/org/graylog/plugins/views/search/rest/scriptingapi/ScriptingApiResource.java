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
package org.graylog.plugins.views.search.rest.scriptingapi;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.ValidationException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.graylog.plugins.views.search.Search;
import org.graylog.plugins.views.search.SearchJob;
import org.graylog.plugins.views.search.permissions.SearchUser;
import org.graylog.plugins.views.search.rest.ExecutionState;
import org.graylog.plugins.views.search.rest.scriptingapi.mapping.AggregationTabularResponseCreator;
import org.graylog.plugins.views.search.rest.scriptingapi.mapping.QueryFailedException;
import org.graylog.plugins.views.search.rest.scriptingapi.mapping.QueryParamsToFullRequestSpecificationMapper;
import org.graylog.plugins.views.search.rest.scriptingapi.request.AggregationRequestSpec;
import org.graylog.plugins.views.search.rest.scriptingapi.request.MessagesRequestSpec;
import org.graylog.plugins.views.search.rest.scriptingapi.response.TabularResponse;
import org.graylog.plugins.views.search.searchtypes.pivot.SortSpec;
import org.graylog2.audit.jersey.NoAuditEvent;
import org.graylog2.plugin.rest.PluginRestResource;
import org.graylog2.shared.rest.resources.RestResource;
import org.graylog2.shared.utilities.StringUtils;

import java.util.List;
import java.util.Set;

import static org.graylog2.shared.rest.documentation.generator.Generator.CLOUD_VISIBLE;
import static org.graylog2.shared.utilities.StringUtils.splitByComma;

@Api(value = "Search/Simple", description = "Simple search API for aggregating and messages retrieval", tags = {CLOUD_VISIBLE})
@Path("/search")
@Consumes({MediaType.APPLICATION_JSON})
@RequiresAuthentication
public class ScriptingApiResource extends RestResource implements PluginRestResource {
    private final ScriptingApiService service;
    private final QueryParamsToFullRequestSpecificationMapper queryParamsToFullRequestSpecificationMapper;

    @Inject
    public ScriptingApiResource(final ScriptingApiService service,
                                final QueryParamsToFullRequestSpecificationMapper queryParamsToFullRequestSpecificationMapper) {
        this.service = service;
        this.queryParamsToFullRequestSpecificationMapper = queryParamsToFullRequestSpecificationMapper;
    }

    @POST
    @ApiOperation(value = "Execute query specified by `queryRequestSpec`",
                  nickname = "messagesByQueryRequestSpec",
                  response = TabularResponse.class)
    @Path("messages")
    @NoAuditEvent("Creating audit event manually in method body.")
    public TabularResponse executeQuery(@ApiParam(name = "queryRequestSpec") @Valid MessagesRequestSpec messagesRequestSpec,
                                        @Context SearchUser searchUser) {
        try {
            return service.executeQuery(messagesRequestSpec, searchUser, getSubject());
        } catch (IllegalArgumentException | ValidationException | QueryFailedException ex) {
            throw new BadRequestException(ex.getMessage(), ex);
        }
    }

    @GET
    @ApiOperation(value = "Execute query specified by query parameters", nickname = "messagesByQueryParameters")
    @Path("messages")
    @NoAuditEvent("Creating audit event manually in method body.")
    public TabularResponse executeQuery(@ApiParam(name = "query", value = "Query (Lucene syntax)", required = true) @QueryParam("query") String query,
                                        @ApiParam(name = "streams", value = "Comma separated list of streams to search in") Set<String> streams,
                                        @ApiParam(name = "stream_categories", value = "Comma separated list of streams categories to search in") @QueryParam("stream_categories") Set<String> streamCategories,
                                        @ApiParam(name = "timerange", value = "Timeframe to search in. See method description.", required = true) @QueryParam("timerange") String timerangeKeyword,
                                        @ApiParam(name = "fields", value = "Fields from the message to show as columns in result") @QueryParam("fields") List<String> fields,
                                        @ApiParam(name = "sort", value = "Field to sort on") @QueryParam("sort") String sort,
                                        @ApiParam(name = "sortOrder", value = "Sort order - asc/desc") @QueryParam("sortOrder") SortSpec.Direction sortOrder,
                                        @ApiParam(name = "from", value = "For paging results. Starting from result") @QueryParam("from") int from,
                                        @ApiParam(name = "size", value = "Page size") @QueryParam("size") int size,
                                        @Context SearchUser searchUser) {

        try {
            MessagesRequestSpec messagesRequestSpec = queryParamsToFullRequestSpecificationMapper.simpleQueryParamsToFullRequestSpecification(query,
                    splitByComma(streams),
                    splitByComma(streamCategories),
                    timerangeKeyword,
                    splitByComma(fields),
                    sort,
                    sortOrder,
                    from,
                    size);
            return service.executeQuery(messagesRequestSpec, searchUser, getSubject());
        } catch (IllegalArgumentException | QueryFailedException ex) {
            throw new BadRequestException(ex.getMessage(), ex);
        }
    }

    @POST
    @ApiOperation(value = "Execute aggregation specified by `searchRequestSpec`",
                  nickname = "aggregateSearchRequestSpec",
                  response = TabularResponse.class)
    @Path("aggregate")
    @NoAuditEvent("Creating audit event manually in method body.")
    public TabularResponse executeQuery(@ApiParam(name = "searchRequestSpec") @Valid AggregationRequestSpec aggregationRequestSpec,
                                        @Context SearchUser searchUser) {
        try {
            return service.executeAggregation(aggregationRequestSpec, searchUser);
        } catch (IllegalArgumentException | ValidationException | QueryFailedException ex) {
            throw new BadRequestException(ex.getMessage(), ex);
        }
    }

    @GET
    @ApiOperation(value = "Execute aggregation specified by query parameters", nickname = "aggregateForQueryParameters")
    @Path("aggregate")
    @NoAuditEvent("Creating audit event manually in method body.")
    public TabularResponse executeQuery(@ApiParam(name = "query", value = "Query (Lucene syntax)", required = true) @QueryParam("query") String query,
                                        @ApiParam(name = "streams", value = "Comma separated list of streams to search in (can be empty)", required = true) @QueryParam("streams") Set<String> streams,
                                        @ApiParam(name = "stream_categories", value = "Comma separated list of streams categories to search in (can be empty)", required = true) @QueryParam("stream_categories") Set<String> streamCategories,
                                        @ApiParam(name = "timerange", value = "Timeframe to search in. See method description.", required = true) @QueryParam("timerange") String timerangeKeyword,
                                        @ApiParam(name = "group_by", value = "Group aggregation by fields/limits.", required = true) @QueryParam("groups") List<String> groups,
                                        @ApiParam(name = "metrics", value = "Metrics to be used.", required = true) @QueryParam("metrics") List<String> metrics,
                                        @Context SearchUser searchUser) {
        try {
            AggregationRequestSpec aggregationRequestSpec = queryParamsToFullRequestSpecificationMapper.simpleQueryParamsToFullRequestSpecification(
                    query,
                    StringUtils.splitByComma(streams),
                    StringUtils.splitByComma(streamCategories),
                    timerangeKeyword,
                    splitByComma(groups),
                    splitByComma(metrics)
            );
            return service.executeAggregation(aggregationRequestSpec, searchUser);
        } catch (IllegalArgumentException | QueryFailedException ex) {
            throw new BadRequestException(ex.getMessage(), ex);
        }
    }
}
