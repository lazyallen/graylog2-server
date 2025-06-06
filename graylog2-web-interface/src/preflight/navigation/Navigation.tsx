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
import React from 'react';
import type { TextProps } from '@mantine/core';
import styled, { css } from 'styled-components';

import HelpMenu from 'preflight/navigation/HelpMenu';
import { Group, AppShell, Text } from 'preflight/components/common';
import NavigationBrand from 'components/perspectives/DefaultBrand';

import ThemeModeToggle from './ThemeModeToggle';

type StyledMantineTextProps = TextProps & {
  children: React.ReactNode;
};
const GraylogHeader = styled(AppShell.Header)(
  ({ theme }) => css`
    background-color: ${theme.colors.global.contentBackground};
    border: 1px solid ${theme.colors.variant.lighter.default};
    padding: ${theme.spacings.md};
  `,
);
const NavigationContainer = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;
const StyledText = styled(Text)<StyledMantineTextProps>(
  ({ theme }) => css`
    color: ${theme.mode === 'light' ? theme.colors.brand.concrete : 'white'};
  `,
);
const Navigation = () => (
  <GraylogHeader>
    <NavigationContainer>
      <Group gap="xs">
        <NavigationBrand />
        <StyledText fw={500} size="xs" mr={1}>
          Graylog Initial Setup
        </StyledText>
      </Group>
      <Group justify="flex-end">
        <HelpMenu />
        <ThemeModeToggle />
      </Group>
    </NavigationContainer>
  </GraylogHeader>
);

export default Navigation;
