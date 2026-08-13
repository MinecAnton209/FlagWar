/*
 * Copyright 2021 TownyAdvanced
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.github.townyadvanced.flagwar.war;

/**
 * The lifecycle phases of a war between two nations.
 */
public enum WarPhase {
    /** No war between the pair of nations. */
    NONE,
    /** War declared; flag placement is blocked until the declaration delay elapses. */
    DECLARED,
    /** Active hostilities; flag placement and capture timers are allowed. */
    ACTIVE,
    /** Truce in effect; captures frozen and new flags blocked. */
    TRUCE,
    /** Peace negotiations in progress; no hostilities. */
    NEGOTIATING,
    /** Peace treaty signed; neutrality period before the war fully ends. */
    PEACE,
    /** Post-peace cooldown before the pair returns to {@link #NONE}. */
    COOLDOWN
}
