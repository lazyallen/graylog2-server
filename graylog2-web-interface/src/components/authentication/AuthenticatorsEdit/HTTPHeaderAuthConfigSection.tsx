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
import { useState, useEffect } from 'react';
import { Formik, Form } from 'formik';

import Routes from 'routing/Routes';
import type HTTPHeaderAuthConfig from 'logic/authentication/HTTPHeaderAuthConfig';
import HTTPHeaderAuthConfigDomain from 'domainActions/authentication/HTTPHeaderAuthConfigDomain';
import { Input, Button, Col, Row, Alert } from 'components/bootstrap';
import { FormikFormGroup, ErrorAlert, Spinner } from 'components/common';
import SectionComponent from 'components/common/Section/SectionComponent';
import useHistory from 'routing/useHistory';
import type { HTTPHeaderAuthConfigJSON } from 'logic/authentication/HTTPHeaderAuthConfig';
import { getPathnameWithoutId } from 'util/URLUtils';
import useSendTelemetry from 'logic/telemetry/useSendTelemetry';
import useLocation from 'routing/useLocation';
import { TELEMETRY_EVENT_TYPE } from 'logic/telemetry/Constants';

const HTTPHeaderAuthConfigSection = () => {
  const [submitError, setSubmitError] = useState<string | undefined>();
  const [loadedConfig, setLoadedConfig] = useState<HTTPHeaderAuthConfig | undefined | void>();
  const sectionTitle = 'Trusted Header Authentication';
  const history = useHistory();
  const { pathname } = useLocation();
  const sendTelemetry = useSendTelemetry();

  const _onSubmit = (data: HTTPHeaderAuthConfigJSON) => {
    sendTelemetry(TELEMETRY_EVENT_TYPE.AUTHENTICATION.CONFIG_UPDATED, {
      app_pathname: getPathnameWithoutId(pathname),
      app_section: 'authenticator-trustedheader',
      app_action_value: 'config-update',
    });

    setSubmitError(undefined);

    return HTTPHeaderAuthConfigDomain.update(data)
      .then(() => {
        history.push(Routes.SYSTEM.AUTHENTICATION.AUTHENTICATORS.SHOW);
      })
      .catch((error) => {
        setSubmitError(error.additional?.res?.text);
      });
  };

  useEffect(() => {
    HTTPHeaderAuthConfigDomain.load().then(setLoadedConfig);
  }, []);

  if (!loadedConfig) {
    return (
      <SectionComponent title={sectionTitle}>
        <Spinner />
      </SectionComponent>
    );
  }

  return (
    <SectionComponent title={sectionTitle}>
      <p>This authenticator enables you to login a user, based on a HTTP header without further interaction.</p>
      <Formik onSubmit={_onSubmit} initialValues={loadedConfig.toJSON()}>
        {({ isSubmitting, isValid }) => (
          <Form className="form form-horizontal">
            <Input id="enable-http-header-auth" labelClassName="col-sm-3" wrapperClassName="col-sm-9" label="Enabled">
              <FormikFormGroup
                label="Enable single sign-on via HTTP header"
                name="enabled"
                formGroupClassName="form-group no-bm"
                wrapperClassName="col-xs-12"
                type="checkbox"
              />
            </Input>
            <FormikFormGroup
              label="Username header"
              name="username_header"
              required
              help="HTTP header containing the implicitly trusted name of the Graylog user. (The header match is ignoring case sensitivity)"
            />
            <Row>
              <Col mdOffset={3} md={9}>
                <Alert bsStyle="info">
                  Please configure the <code>trusted_proxies</code> setting in the Graylog server configuration file.
                </Alert>
              </Col>
            </Row>
            <ErrorAlert runtimeError>{submitError}</ErrorAlert>
            <Row className="no-bm">
              <Col xs={12}>
                <div className="pull-right">
                  <Button bsStyle="success" disabled={isSubmitting || !isValid} title="Update Config" type="submit">
                    Update Config
                  </Button>
                </div>
              </Col>
            </Row>
          </Form>
        )}
      </Formik>
    </SectionComponent>
  );
};

export default HTTPHeaderAuthConfigSection;
