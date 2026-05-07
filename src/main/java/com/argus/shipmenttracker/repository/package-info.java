/**
 * Spring Data JPA repositories. All custom queries scope by company_id to
 * preserve multi-tenant isolation; never query across tenants here.
 */
package com.argus.shipmenttracker.repository;
