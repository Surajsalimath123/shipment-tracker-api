/**
 * Business-logic layer. Services orchestrate repository calls, enforce
 * tenant isolation against {@code TenantContext}, and trigger asynchronous
 * webhook dispatch on state changes.
 */
package com.argus.shipmenttracker.service;
