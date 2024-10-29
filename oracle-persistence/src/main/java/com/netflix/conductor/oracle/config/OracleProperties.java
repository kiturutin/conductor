/*
 * Copyright 2023 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.oracle.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

@Setter
@ConfigurationProperties("conductor.oracle")
public class OracleProperties {

    /** The time in seconds after which the in-memory task definitions cache will be refreshed */
    @Getter
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration taskDefCacheRefreshInterval = Duration.ofSeconds(60);

    @Getter
    private Integer deadlockRetryMax = 3;

    @Getter
    @DurationUnit(ChronoUnit.MILLIS)
    private Duration pollDataFlushInterval = Duration.ofMillis(0);

    @Getter
    @DurationUnit(ChronoUnit.MILLIS)
    private Duration pollDataCacheValidityPeriod = Duration.ofMillis(0);

    private boolean experimentalQueueNotify = false;

    @Getter
    private Integer experimentalQueueNotifyStalePeriod = 5000;

    private boolean onlyIndexOnStatusChange = false;

    /** The boolean indicating whether data migrations should be executed */
    @Getter
    private boolean applyDataMigrations = true;

    @Getter
    public String schema = "public";

    public boolean allowFullTextQueries = true;

    public boolean allowJsonQueries = true;

    /** The maximum number of threads allowed in the async pool */
    @Getter
    private int asyncMaxPoolSize = 12;

    /** The size of the queue used for holding async indexing tasks */
    @Getter
    private int asyncWorkerQueueSize = 100;

    public boolean getExperimentalQueueNotify() {
        return experimentalQueueNotify;
    }

    public boolean getOnlyIndexOnStatusChange() {
        return onlyIndexOnStatusChange;
    }

    public boolean getAllowFullTextQueries() {
        return allowFullTextQueries;
    }

    public boolean getAllowJsonQueries() {
        return allowJsonQueries;
    }

}
