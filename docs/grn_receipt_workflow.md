# Goods Received Note (GRN) & Receipt Workflow

This document outlines the workflow for receiving inventory from suppliers into the Central Store. This process tracks expected deliveries, records actual receipts, manages discrepancies (variances), and feeds into supplier performance metrics.

## 1. Expected Receipt Creation

The process begins when an order is placed with a supplier. The Central Store Manager or System Administrator creates an `ExpectedReceipt` in the system to anticipate the delivery.

**Actions:**
- Target endpoint: `POST /api/expected-receipts`
- The user selects the Central Store, enters the Supplier Name, sets an Expected Date, and adds the expected `lines` (items and quantities).
- Status: Initialized as `AWAITING_GRN`.

> [!NOTE]
> The `supplierName` is currently recorded as free-text on the Expected Receipt. This field is used for grouping supplier performance metrics later.

## 2. Status Updates & Tracking

Before the goods arrive, the status of the Expected Receipt can be manually updated to reflect its progress.

**Actions:**
- Target endpoint: `PATCH /api/expected-receipts/{id}/status`
- Allowable states before receipt: `DELAYED`, `IN_TRANSIT`, `SUPPLIER_CONFIRMED`.
- *Note:* The system prevents manual rollback from a received state (`PARTIALLY_RECEIVED` or `COMPLETED`).

## 3. Receiving Goods (Confirming GRN)

When the physical delivery arrives at the Central Store, warehouse staff confirm the receipt. This action generates the official Goods Received Note (GRN).

**Actions:**
- Target endpoint: `POST /api/expected-receipts/{id}/confirm`
- **Clean Receipt:** If no overrides are provided in the payload, the system assumes all items were received in full and in `GOOD` condition.
- **Partial/Damaged Receipt:** Staff can pass specific line overrides specifying `receivedQuantity` and `condition` (e.g., `GOOD` or `DAMAGED`).

**System Impacts:**
- A `GoodsReceivedNote` entity is created.
- The `ExpectedReceipt` status updates to `COMPLETED` (if fully received) or `PARTIALLY_RECEIVED` (if there was a variance).
- **Inventory Updates:**
  - `GOOD` items are added to the store's `quantityOnHand`.
  - `DAMAGED` items are added to the store's `quantityDamaged`.
- **Discrepancies:** If the received quantity is less than expected, a `Discrepancy` record is automatically generated, and the variance amount is tracked in `quantityFrozen`.
- **Notifications:** If there is a variance, a notification is sent to the Procurement team (Central Store Managers).

## 4. Documentation & Reporting

- **GRN Document:** A PDF version of the Goods Received Note can be downloaded via `GET /api/expected-receipts/{id}/grn`.
- **Supplier Performance:** The system aggregates all historical Expected Receipts and GRNs to compute metrics such as:
  - Average lead time (days from creation to receipt)
  - Accuracy percentage (lines perfectly fulfilled)
  - Defect percentage (lines received as damaged)
  This report is accessible at `GET /api/reports/supplier-performance`.
