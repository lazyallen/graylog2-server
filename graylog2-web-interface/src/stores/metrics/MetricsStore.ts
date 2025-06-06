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
import Reflux from 'reflux';

import * as URLUtils from 'util/URLUtils';
import ApiRoutes from 'routing/ApiRoutes';
import fetch, { fetchPeriodically } from 'logic/rest/FetchProvider';
import TimeHelper from 'util/TimeHelper';
import { singletonStore, singletonActions } from 'logic/singleton';
import { NodesStore } from 'stores/nodes/NodesStore';
import { SessionActions, SessionStore } from 'stores/sessions/SessionStore';

type MetricsActionsType = {
  add: (nodeId: string, metricName: string) => Promise<unknown>;
  addGlobal: (name: string) => Promise<unknown>;
  clear: () => Promise<unknown>;
  filter: () => Promise<unknown>;
  list: () => Promise<unknown>;
  names: () => Promise<unknown>;
  remove: (nodeId: string, metricName: string) => Promise<unknown>;
  removeGlobal: (name: string) => Promise<unknown>;
};
export const MetricsActions = singletonActions('core.Metrics', () =>
  Reflux.createActions<MetricsActionsType>({
    add: { asyncResult: true },
    addGlobal: { asyncResult: true },
    clear: { asyncResult: true },
    filter: { asyncResult: true },
    list: { asyncResult: true },
    names: { asyncResult: true },
    remove: { asyncResult: true },
    removeGlobal: { asyncResult: true },
  }),
);

export type CounterMetric = {
  metric: {
    count: number;
  };
  type: 'counter';
};

export type GaugeMetric = {
  metric: {
    value: number;
  };
  type: 'gauge';
};

export type Rate = {
  rate: {
    total: number;
    mean: number;
    one_minute: number;
    five_minute: number;
    fifteen_minute: number;
  };
  rate_unit: string;
};

export type MeterMetric = {
  metric: Rate;
  type: 'meter';
};

type Timing = {
  '95th_percentile': number;
  '98th_percentile': number;
  '99th_percentile': number;
  'std_dev': number;
  mean: number;
  min: number;
  max: number;
};

export type TimerMetric = {
  metric: Rate & {
    time: Timing;
  };
  type: 'timer';
};

export type HistogramMetric = {
  metric: {
    time: Timing;
    count: number;
  };
  type: 'histogram';
};

type BaseMetric<T> = {
  full_name: string;
  name: string;
} & T;

export type Metric =
  | BaseMetric<CounterMetric>
  | BaseMetric<GaugeMetric>
  | BaseMetric<MeterMetric>
  | BaseMetric<TimerMetric>
  | BaseMetric<HistogramMetric>;

export type NodeMetric = {
  [metricName: string]: Metric;
};

export type ClusterMetric = {
  [nodeId: string]: NodeMetric;
};

type MetricsStoreState = {
  metrics: ClusterMetric;
};
export const MetricsStore = singletonStore('core.Metrics', () =>
  Reflux.createStore<MetricsStoreState>({
    listenables: [MetricsActions, SessionActions],
    namespace: 'org',
    registrations: {},
    globalRegistrations: {},
    promises: {},

    init() {
      this.listenTo(NodesStore, this.updateNodes);
    },
    getInitialState() {
      return { metricsNames: this.metricsNames, metrics: this.metrics };
    },
    updateNodes(update) {
      this.nodes = update.nodes;
    },
    _allResults(promises) {
      const accumulator = [];
      let result = Promise.resolve(null);

      promises.forEach((promise) => {
        result = result
          .then(() => promise)
          .then(
            (value) => accumulator.push(value),
            (error) => accumulator.push(error),
          );
      });

      return result.then(() => accumulator);
    },
    _metricsToFetch(localRegistrations, globalRegistrations) {
      const metricsToFetch = {};

      // First collect all node metric registrations
      Object.keys(localRegistrations)
        .filter((nodeId) => Object.keys(localRegistrations[nodeId]).length > 0)
        .forEach((nodeId) => {
          Object.keys(localRegistrations[nodeId])
            .filter((metricName) => localRegistrations[nodeId][metricName] > 0)
            .forEach((metricName) => {
              metricsToFetch[metricName] = 1;
            });
        });

      // Then collect all global metric registrations
      Object.keys(globalRegistrations)
        .filter((metricName) => globalRegistrations[metricName] > 0)
        .forEach((metricName) => {
          metricsToFetch[metricName] = 1;
        });

      return metricsToFetch;
    },
    _buildMetricsFromResponse(response) {
      const metrics = {};

      Object.keys(response).forEach((nodeId) => {
        const nodeMetrics = {};

        if (!response[nodeId]) {
          return;
        }

        response[nodeId].metrics.forEach((metric) => {
          nodeMetrics[metric.full_name] = metric;
        });

        metrics[nodeId] = nodeMetrics;
      });

      return metrics;
    },
    list() {
      if (!SessionStore.isLoggedIn()) {
        return null;
      }

      const metricsToFetch = this._metricsToFetch(this.registrations, this.globalRegistrations);
      const url = URLUtils.qualifyUrl(ApiRoutes.ClusterMetricsApiController.multipleAllNodes().url);

      if (!this.promises.list) {
        const promise = fetchPeriodically('POST', url, { metrics: Object.keys(metricsToFetch) }).finally(
          () => delete this.promises.list,
        );

        promise.then((response) => {
          this.metrics = this._buildMetricsFromResponse(response);
          // The metricsUpdatedAt value is used by components to decide if they should be re-rendered
          this.trigger({ metrics: this.metrics, metricsUpdatedAt: TimeHelper.nowInSeconds() });

          return this.metrics;
        });

        this.promises.list = promise;
      }

      MetricsActions.list.promise(this.promises.list);

      return this.promises.list;
    },
    names() {
      if (!this.nodes) {
        // eslint-disable-next-line no-console
        console.warn('Node list not yet available, not fetching metrics.');

        return;
      }

      const promise = this._allResults(
        Object.keys(this.nodes).map((nodeId) => {
          const url = URLUtils.qualifyUrl(
            ApiRoutes.ClusterMetricsApiController.byNamespace(nodeId, this.namespace).url,
          );

          return fetch('GET', url).then(
            (response) => ({ nodeId: nodeId, names: response.metrics }),
            // When fetching metrics fails, keep previous available metrics around, letting user see them
            (error) => ({ nodeId: nodeId, names: this.metricsNames[nodeId], error: error }),
          );
        }),
      ).then((responses) => {
        const metricsNames = {};
        const metricsErrors = {};

        responses.forEach((response) => {
          if (response.nodeId) {
            metricsNames[response.nodeId] = response.names;
            metricsErrors[response.nodeId] = response.error;
          }
        });

        this.trigger({ metricsNames: metricsNames, metricsErrors: metricsErrors });
        this.metricsNames = metricsNames;

        return metricsNames;
      });

      MetricsActions.names.promise(promise);
    },
    add(nodeId, metricName) {
      if (!this.registrations[nodeId]) {
        this.registrations[nodeId] = {};
      }

      this.registrations[nodeId][metricName] = this.registrations[nodeId][metricName]
        ? this.registrations[nodeId][metricName] + 1
        : 1;
    },
    addGlobal(metricName) {
      if (!this.globalRegistrations[metricName]) {
        this.globalRegistrations[metricName] = 1;
      } else {
        this.globalRegistrations[metricName] += 1;
      }
    },
    remove(nodeId, metricName) {
      if (!this.registrations[nodeId]) {
        return;
      }

      this.registrations[nodeId][metricName] =
        this.registrations[nodeId][metricName] > 0 ? this.registrations[nodeId][metricName] - 1 : 0;

      if (this.registrations[nodeId][metricName] === 0) {
        delete this.registrations[nodeId][metricName];
      }
    },
    removeGlobal(metricName) {
      if (!this.globalRegistrations[metricName]) {
        return;
      }

      this.globalRegistrations[metricName] =
        this.globalRegistrations[metricName] > 0 ? this.globalRegistrations[metricName] - 1 : 0;

      if (this.globalRegistrations[metricName] === 0) {
        delete this.globalRegistrations[metricName];
      }
    },
  }),
);
