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
import * as React from 'react';
import styled, { css } from 'styled-components';

import { Col, Row } from 'components/bootstrap';
import { Pluralize } from 'components/common';

const SimulationChangesWrap = styled.div`
  padding-top: 15px;

  dl {
    margin-bottom: 10px;
    margin-top: 5px;
  }

  dd {
    padding: 1px 9px 3px;
  }

  dt {
    margin-top: 1px;
    padding: 3px 9px 1px;

    &::after {
      content: ': ';
    }

    &:first-child {
      border-radius: 4px 4px 0 0;
    }

    ~ dd:last-child {
      border-radius: 0 0 4px 4px;
    }
  }
`;

const OriginalChanges = styled.div`
  margin-top: 10px;
`;

const FieldResultWrap = styled.div<{ resultType: string }>(({ resultType, theme }) => {
  const { success, danger, info } = theme.colors.variant.light;
  const types = {
    added: success,
    removed: danger,
    mutated: info,
  };

  return `
    dt,
    dd {
      background-color: ${types[resultType]};
      color: ${theme.utils.contrastingColor(types[resultType])};
    }
  `;
});

const FieldValue = styled.dd<{ removed: boolean }>(
  ({ removed, theme }) => css`
    font-family: ${theme.fonts.family.monospace};

    ${removed &&
    css`
      text-decoration: line-through;
      font-style: italic;
    `}
  `,
);

type Props = {
  originalMessage: {
    id: string;
  };
  simulationResults: {
    messages: any[];
  };
};

const SimulationChanges = (props: Props) => {
  const _isOriginalMessageRemoved = (originalMessage, processedMessages) =>
    !processedMessages.find((message) => message.id === originalMessage.id);

  const _formatFieldTitle = (field) => <dt key={`${field}-key`}>{field}</dt>;

  const _formatFieldValue = (field, value, isRemoved = false) => (
    <FieldValue key={`${field}-value`} removed={isRemoved}>
      {String(value)}
    </FieldValue>
  );

  const _formatAddedFields = (addedFields) => {
    const keys = Object.keys(addedFields);

    if (keys.length === 0) {
      return null;
    }

    const formattedFields = [];

    keys.sort().forEach((field) => {
      formattedFields.push(_formatFieldTitle(field));
      formattedFields.push(_formatFieldValue(field, addedFields[field]));
    });

    return (
      <FieldResultWrap resultType="added">
        <h4>Added fields</h4>
        <dl>{formattedFields}</dl>
      </FieldResultWrap>
    );
  };

  const _formatRemovedFields = (removedFields) => {
    const keys = Object.keys(removedFields);

    if (keys.length === 0) {
      return null;
    }

    const formattedFields = [];

    keys.sort().forEach((field) => {
      formattedFields.push(_formatFieldTitle(field));
      formattedFields.push(_formatFieldValue(field, removedFields[field]));
    });

    return (
      <FieldResultWrap resultType="removed">
        <h4>Removed fields</h4>
        <dl>{formattedFields}</dl>
      </FieldResultWrap>
    );
  };

  const _formatMutatedFields = (mutatedFields) => {
    const keys = Object.keys(mutatedFields);

    if (keys.length === 0) {
      return null;
    }

    const formattedFields = [];

    keys.sort().forEach((field) => {
      formattedFields.push(_formatFieldTitle(field));
      formattedFields.push(_formatFieldValue(`${field}-original`, mutatedFields[field].before, true));
      formattedFields.push(_formatFieldValue(field, mutatedFields[field].after));
    });

    return (
      <FieldResultWrap resultType="mutated">
        <h4>Mutated fields</h4>
        <dl>{formattedFields}</dl>
      </FieldResultWrap>
    );
  };

  const _getOriginalMessageChanges = () => {
    const { originalMessage, simulationResults } = props;
    const processedMessages = simulationResults.messages;

    if (_isOriginalMessageRemoved(originalMessage, processedMessages)) {
      return <p>Original message would be dropped during processing.</p>;
    }

    const processedMessage = processedMessages.find((message) => message.id === originalMessage.id);

    const formattedAddedFields = _formatAddedFields(processedMessage.decoration_stats.added_fields);
    const formattedRemovedFields = _formatRemovedFields(processedMessage.decoration_stats.removed_fields);
    const formattedMutatedFields = _formatMutatedFields(processedMessage.decoration_stats.changed_fields);

    if (!formattedAddedFields && !formattedRemovedFields && !formattedMutatedFields) {
      return <p>Original message would be not be modified during processing.</p>;
    }

    return (
      <OriginalChanges>
        {formattedAddedFields}
        {formattedRemovedFields}
        {formattedMutatedFields}
      </OriginalChanges>
    );
  };

  const _formatOriginalMessageChanges = () => {
    const { originalMessage } = props;

    return (
      <Row className="row-sm">
        <Col md={12}>
          <h3>
            Changes in original message{' '}
            <small>
              <em>{originalMessage.id}</em>
            </small>
          </h3>
          {_getOriginalMessageChanges()}
        </Col>
      </Row>
    );
  };

  const _formatOtherChanges = () => {
    const { originalMessage, simulationResults } = props;

    const createdMessages = simulationResults.messages.filter((message) => message.id !== originalMessage.id);

    if (createdMessages.length === 0) {
      return null;
    }

    return (
      <Row className="row-sm">
        <Col md={12}>
          <h3>Other changes</h3>
          <p>
            There would be {createdMessages.length}{' '}
            <Pluralize singular="message" plural="messages" value={createdMessages.length} /> created. Switch to the{' '}
            <em>Results preview</em> view option to see{' '}
            <Pluralize singular="it" plural="them" value={createdMessages.length} />.
          </p>
        </Col>
      </Row>
    );
  };

  return (
    <SimulationChangesWrap>
      {_formatOriginalMessageChanges()}
      {_formatOtherChanges()}
    </SimulationChangesWrap>
  );
};

export default SimulationChanges;
