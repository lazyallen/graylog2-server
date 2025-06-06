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
package org.graylog.storage.elasticsearch7;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Streams;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.graylog.events.event.EventDto;
import org.graylog.events.processor.EventProcessorException;
import org.graylog.events.search.MoreSearch;
import org.graylog.events.search.MoreSearchAdapter;
import org.graylog.plugins.views.search.searchfilters.model.UsedSearchFilter;
import org.graylog.plugins.views.search.searchtypes.pivot.buckets.AutoInterval;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.action.search.SearchRequest;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.action.search.SearchResponse;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.action.support.IndicesOptions;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.common.xcontent.ToXContent;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.index.query.BoolQueryBuilder;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.index.query.QueryBuilder;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.index.query.QueryBuilders;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.search.aggregations.AggregationBuilders;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.search.aggregations.bucket.histogram.ExtendedBounds;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.search.aggregations.bucket.histogram.ParsedAutoDateHistogram;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.search.aggregations.bucket.terms.ParsedTerms;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.search.builder.SearchSourceBuilder;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.search.sort.FieldSortBuilder;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.search.sort.SortOrder;
import org.graylog2.indexer.results.ChunkedResult;
import org.graylog2.indexer.results.MultiChunkResultRetriever;
import org.graylog2.indexer.results.ResultChunk;
import org.graylog2.indexer.results.ResultMessage;
import org.graylog2.indexer.searches.ChunkCommand;
import org.graylog2.indexer.searches.Sorting;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.Tools;
import org.graylog2.plugin.indexer.searches.timeranges.AbsoluteRange;
import org.graylog2.plugin.indexer.searches.timeranges.TimeRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.requireNonNull;
import static org.graylog.shaded.elasticsearch7.org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.graylog.shaded.elasticsearch7.org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.graylog.shaded.elasticsearch7.org.elasticsearch.index.query.QueryBuilders.queryStringQuery;
import static org.graylog.shaded.elasticsearch7.org.elasticsearch.index.query.QueryBuilders.termsQuery;

public class MoreSearchAdapterES7 implements MoreSearchAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(MoreSearchAdapterES7.class);
    public static final IndicesOptions INDICES_OPTIONS = IndicesOptions.LENIENT_EXPAND_OPEN;
    private static final String termsAggregationName = "alert_type";
    private static final String histogramAggregationName = "histogram";
    private final ES7ResultMessageFactory resultMessageFactory;
    private final ElasticsearchClient client;
    private final Boolean allowLeadingWildcard;
    private final SortOrderMapper sortOrderMapper;
    private final MultiChunkResultRetriever multiChunkResultRetriever;

    @Inject
    public MoreSearchAdapterES7(ES7ResultMessageFactory resultMessageFactory,
                                ElasticsearchClient client,
                                @Named("allow_leading_wildcard_searches") Boolean allowLeadingWildcard,
                                SortOrderMapper sortOrderMapper,
                                MultiChunkResultRetriever multiChunkResultRetriever) {
        this.resultMessageFactory = resultMessageFactory;
        this.client = client;
        this.allowLeadingWildcard = allowLeadingWildcard;
        this.sortOrderMapper = sortOrderMapper;
        this.multiChunkResultRetriever = multiChunkResultRetriever;
    }

    @Override
    public MoreSearch.Result eventSearch(String queryString, TimeRange timerange, Set<String> affectedIndices,
                                         Sorting sorting, int page, int perPage, Set<String> eventStreams,
                                         String filterString, Set<String> forbiddenSourceStreams, Map<String, Set<String>> extraFilters) {
        final var filter = createQuery(queryString, timerange, eventStreams, filterString, forbiddenSourceStreams, extraFilters);

        final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                .query(filter)
                .from((page - 1) * perPage)
                .size(perPage)
                .trackTotalHits(true);

        final var sortBuilders = createSorting(sorting);
        sortBuilders.forEach(searchSourceBuilder::sort);

        final Set<String> indices = affectedIndices.isEmpty() ? Collections.singleton("") : affectedIndices;
        final SearchRequest searchRequest = new SearchRequest(indices.toArray(new String[0]))
                .source(searchSourceBuilder)
                .indicesOptions(INDICES_OPTIONS);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Query:\n{}", searchSourceBuilder.toString(new ToXContent.MapParams(Collections.singletonMap("pretty", "true"))));
            LOG.debug("Execute search: {}", searchRequest);
        }

        final SearchResponse searchResult = client.search(searchRequest, "Unable to perform search query");

        final List<ResultMessage> hits = Streams.stream(searchResult.getHits())
                .map(resultMessageFactory::fromSearchHit)
                .collect(Collectors.toList());

        final long total = searchResult.getHits().getTotalHits().value;

        return MoreSearch.Result.builder()
                .results(hits)
                .resultsCount(total)
                .duration(searchResult.getTook().getMillis())
                .usedIndexNames(affectedIndices)
                .executedQuery(searchSourceBuilder.toString())
                .build();
    }

    @Override
    public MoreSearch.Histogram eventHistogram(String queryString, AbsoluteRange timerange, Set<String> affectedIndices,
                                               Set<String> eventStreams, String filterString, Set<String> forbiddenSourceStreams, ZoneId timeZone,
                                               Map<String, Set<String>> extraFilters) {
        final var filter = createQuery(queryString, timerange, eventStreams, filterString, forbiddenSourceStreams, extraFilters);

        final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                .query(filter)
                .size(0)
                .trackTotalHits(true);

        final var autoInterval = AutoInterval.create();
        final var interval = autoInterval.toDateInterval(timerange);

        final var histogramAggregation = new DateHistogramAggregationBuilder(histogramAggregationName)
                .field(EventDto.FIELD_EVENT_TIMESTAMP)
                .timeZone(timeZone)
                .minDocCount(0)
                .extendedBounds(new ExtendedBounds(Tools.buildElasticSearchTimeFormat(timerange.from()), Tools.buildElasticSearchTimeFormat(timerange.to())));

        final var dateInterval = new DateHistogramInterval(interval.getQuantity().toString() + interval.getUnit());

        if (interval.getQuantity().intValue() > 1) {
            histogramAggregation.fixedInterval(dateInterval);
        } else {
            histogramAggregation.calendarInterval(dateInterval);
        }

        final var termsAggregation = AggregationBuilders.terms(termsAggregationName)
                .field(EventDto.FIELD_ALERT);

        searchSourceBuilder.aggregation(histogramAggregation.subAggregation(termsAggregation));

        final Set<String> indices = affectedIndices.isEmpty() ? Collections.singleton("") : affectedIndices;
        final SearchRequest searchRequest = new SearchRequest(indices.toArray(new String[0]))
                .source(searchSourceBuilder)
                .indicesOptions(INDICES_OPTIONS);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Query:\n{}", searchSourceBuilder.toString(new ToXContent.MapParams(Collections.singletonMap("pretty", "true"))));
            LOG.debug("Execute search: {}", searchRequest);
        }

        final SearchResponse searchResult = client.search(searchRequest, "Unable to perform search query");

        final ParsedAutoDateHistogram histogramResult = searchResult.getAggregations().get(histogramAggregationName);
        final var histogramBuckets = histogramResult.getBuckets();

        final var alerts = new ArrayList<MoreSearch.Histogram.Bucket>(histogramBuckets.size());
        final var events = new ArrayList<MoreSearch.Histogram.Bucket>(histogramBuckets.size());

        histogramBuckets.forEach(bucket -> {
            final var parsedTerms = (ParsedTerms) bucket.getAggregations().get(termsAggregationName);
            final var dateTime = (ZonedDateTime) bucket.getKey();
            final var alertCount = Optional.ofNullable(parsedTerms.getBucketByKey("true")).map(MultiBucketsAggregation.Bucket::getDocCount).orElse(0L);
            final var eventCount = Optional.ofNullable(parsedTerms.getBucketByKey("false")).map(MultiBucketsAggregation.Bucket::getDocCount).orElse(0L);
            alerts.add(new MoreSearch.Histogram.Bucket(dateTime, alertCount));
            events.add(new MoreSearch.Histogram.Bucket(dateTime, eventCount));
        });

        return new MoreSearch.Histogram(new MoreSearch.Histogram.EventsBuckets(events, alerts));
    }

    private QueryBuilder createQuery(String queryString, TimeRange timerange, Set<String> eventStreams, String filterString,
                                     Set<String> forbiddenSourceStreams, Map<String, Set<String>> extraFilters) {
        final QueryBuilder query = (queryString.isEmpty() || queryString.equals("*"))
                ? matchAllQuery()
                : queryStringQuery(queryString).allowLeadingWildcard(allowLeadingWildcard);

        final BoolQueryBuilder filter = boolQuery()
                .filter(query)
                .filter(termsQuery(EventDto.FIELD_STREAMS, eventStreams))
                .filter(requireNonNull(TimeRangeQueryFactory.create(timerange)));

        extraFilters.entrySet()
                .stream()
                .flatMap(extraFilter -> extraFilter.getValue()
                        .stream()
                        .map(value -> buildExtraFilter(extraFilter.getKey(), value)))
                .forEach(filter::filter);

        if (!isNullOrEmpty(filterString)) {
            filter.filter(queryStringQuery(filterString));
        }

        if (!forbiddenSourceStreams.isEmpty()) {
            // If an event has any stream in "source_streams" that the calling search user is not allowed to access,
            // the event must not be in the search result.
            filter.filter(boolQuery().mustNot(termsQuery(EventDto.FIELD_SOURCE_STREAMS, forbiddenSourceStreams)));
        }

        return filter;
    }

    private QueryBuilder buildExtraFilter(String field, String value) {
        return QueryBuilders.multiMatchQuery(value, field);
    }

    private List<FieldSortBuilder> createSorting(Sorting sorting) {
        final SortOrder order = sortOrderMapper.fromSorting(sorting);
        final List<FieldSortBuilder> sortBuilders;
        if (EventDto.FIELD_TIMERANGE_START.equals(sorting.getField())) {
            sortBuilders = List.of(
                    new FieldSortBuilder(EventDto.FIELD_TIMERANGE_START),
                    new FieldSortBuilder(EventDto.FIELD_TIMERANGE_END)
            );
        } else {
            sortBuilders = List.of(new FieldSortBuilder(sorting.getField()));
        }
        return sortBuilders.stream()
                .map(sortBuilder -> {
                    sorting.getUnmappedType().ifPresent(unmappedType -> sortBuilder
                            .unmappedType(unmappedType)
                            .missing(order.equals(SortOrder.ASC) ? "_first" : "_last"));
                    return sortBuilder.order(order);
                })
                .toList();
    }

    @Override
    public void scrollEvents(String queryString, TimeRange timeRange, Set<String> affectedIndices, Set<String> streams,
                             List<UsedSearchFilter> filters, int batchSize, ScrollEventsCallback resultCallback) throws EventProcessorException {
        final ChunkCommand chunkCommand = buildScrollCommand(queryString, timeRange, affectedIndices, filters, streams, batchSize);

        final ChunkedResult chunkedResult = multiChunkResultRetriever.retrieveChunkedResult(chunkCommand);

        final AtomicBoolean continueScrolling = new AtomicBoolean(true);

        final Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            ResultChunk resultChunk = chunkedResult.nextChunk();
            while (continueScrolling.get() && resultChunk != null) {
                final List<ResultMessage> messages = resultChunk.messages();

                LOG.debug("Passing <{}> messages to callback", messages.size());
                resultCallback.accept(Collections.unmodifiableList(messages), continueScrolling);

                // Stop if the resultCallback told us to stop
                if (!continueScrolling.get()) {
                    break;
                }

                resultChunk = chunkedResult.nextChunk();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            try {
                // Tell Elasticsearch that we are done with the scroll so it can release resources as soon as possible
                // instead of waiting for the scroll timeout to kick in.
                chunkedResult.cancel();
            } catch (Exception ignored) {
            }
            LOG.debug("Scrolling done - took {} ms", stopwatch.stop().elapsed(TimeUnit.MILLISECONDS));
        }
    }

    private ChunkCommand buildScrollCommand(String queryString, TimeRange timeRange, Set<String> affectedIndices, List<UsedSearchFilter> filters, Set<String> streams, int batchSize) {
        ChunkCommand.Builder commandBuilder = ChunkCommand.builder()
                .query(queryString)
                .range(timeRange)
                .indices(affectedIndices)
                .filters(filters == null ? Collections.emptyList() : filters)
                .batchSize(batchSize)
                // For correlation need the oldest messages to come in first
                .sorting(new Sorting(Message.FIELD_TIMESTAMP, Sorting.Direction.ASC));

        if (!streams.isEmpty()) {
            commandBuilder = commandBuilder.streams(streams);
        }

        return commandBuilder
                .build();
    }
}
