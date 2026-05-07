/**
 * Asynchronous webhook delivery. Events are queued on a bounded thread
 * pool, signed with HMAC-SHA256, and retried with exponential backoff;
 * each attempt is logged to {@code webhook_delivery_logs}.
 */
package com.argus.shipmenttracker.webhook;
