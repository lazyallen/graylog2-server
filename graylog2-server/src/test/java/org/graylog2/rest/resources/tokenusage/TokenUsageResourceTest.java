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
package org.graylog2.rest.resources.tokenusage;

import org.graylog2.database.PaginatedList;
import org.graylog2.rest.models.SortOrder;
import org.graylog2.rest.models.tokenusage.TokenUsageDTO;
import org.graylog2.rest.models.tools.responses.PageListResponse;
import org.graylog2.search.SearchQuery;
import org.graylog2.security.AccessTokenEntity;
import org.graylog2.shared.tokenusage.TokenUsageService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;


public class TokenUsageResourceTest {
    public static final int PAGE = 1;
    public static final int PER_PAGE = 10;


    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private TokenUsageService tokenUsageService;

    private TokenUsageResource testee;

    @Before
    public void setUp() {
        testee = new TokenUsageResource(tokenUsageService);
    }

    @Test
    public void callingEndpointCallsService() {
        final String query = "";
        final PaginatedList<TokenUsageDTO> paginatedList = mkPaginatedList();
        final SortOrder order = SortOrder.ASCENDING;
        final String sort = AccessTokenEntity.FIELD_NAME;
        when(tokenUsageService.loadTokenUsage(eq(PAGE), eq(PER_PAGE), any(SearchQuery.class), eq(sort), eq(order)))
                .thenReturn(paginatedList);

        final PageListResponse<TokenUsageDTO> actual = testee.getPage(PAGE, PER_PAGE, query, sort, order);

        final PageListResponse<TokenUsageDTO> expected = PageListResponse.create(query, paginatedList.pagination(), paginatedList.pagination().total(), sort, order, paginatedList, TokenUsageResource.ATTRIBUTES, TokenUsageResource.SETTINGS);
        assertEquals(expected, actual);

        verify(tokenUsageService, times(1)).loadTokenUsage(eq(PAGE), eq(PER_PAGE), any(SearchQuery.class), eq(sort), eq(order));
        verifyNoMoreInteractions(tokenUsageService);
    }

    private PaginatedList<TokenUsageDTO> mkPaginatedList() {
        return PaginatedList.emptyList(PAGE, PER_PAGE);
    }
}
