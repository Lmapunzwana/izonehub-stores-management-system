# Issuance & Return Workflow

This document outlines the workflow for issuing inventory out to employees/projects via Material Issue Vouchers (MIVs), and the process for employees returning unused or damaged materials via Stock Returns.

## 1. Material Issue Voucher (MIV) Creation

When physical stock needs to be handed over to an employee for use on a specific project, an MIV is generated.

**Actions:**
- Target endpoint: `POST /api/issuances`
- **Details:** The Site Store Manager specifies the issuing Store, the assigned Project, and the Employee receiving the materials. They then specify the items and the `issuedQuantity` for each.
- **System Impacts:**
  - The MIV status is set to `ISSUED`.
  - The requested items are permanently deducted from the Site Store's `quantityOnHand` via `inventory.dispatch()`.
  - An Audit log and MIV document are generated.

## 2. Returning Materials to Store

If an employee does not consume all issued materials, or if items are damaged during the project, they must be returned to the store via a Stock Return.

There are two primary ways to initiate a return:

### A. Returning against an MIV (Standard Flow)
Employees returning items previously issued to them.

**Actions:**
- Target endpoint: `POST /api/returns`
- The system validates that the user is not attempting to return *more* than what was originally issued to them in the specific MIV (minus any items already returned).
- A pending `StockReturn` is created in state `PENDING_CONFIRMATION`.
- **System Impacts:** To track the physical movement, the items are moved into `quantityInTransit` from the Site Store to the Central Store (or back to the Site Store depending on the routing).

### B. Standalone Return to Central
When a site store has excess stock or a project completes, the site store manager can initiate a direct return to the central warehouse without referencing a specific MIV.

**Actions:**
- Target endpoint: `POST /api/material-requests/standalone-return`
- The system automatically creates an auto-approved, auto-dispatched `MaterialRequest` routing the stock from the Site Store to the Central Store.

## 3. Confirming the Return

Once the returned physical goods arrive and are inspected, the return must be confirmed by the receiving manager.

**Actions:**
- Target endpoint: `POST /api/returns/{id}/confirm`
- The receiving manager provides the `receivedQuantity` for each returned line item.
- **System Impacts:**
  - The `StockReturn` status updates to `CONFIRMED`.
  - **Clean Receipt:** If the expected return quantity matches the physical receipt, the `quantityInTransit` is cleared.
  - **Condition Handling:**
    - `SERVICEABLE`: The items are added back into the receiving store's `quantityOnHand`.
    - `UNSERVICEABLE`: The items are routed to the store's `quantityDamaged` bucket.
  - **Variances:** If the employee initiated a return for 10 items but only handed over 8, the missing 2 items are flagged as a `Discrepancy` and frozen in the store's inventory pending investigation.
  - The original MIV (if applicable) is marked as `PARTIALLY_RETURNED` or updated with the returned quantities.
